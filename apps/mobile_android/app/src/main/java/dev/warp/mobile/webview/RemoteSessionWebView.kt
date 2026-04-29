package dev.warp.mobile.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.warp.mobile.auth.AuthHandoffProvider
import dev.warp.mobile.auth.AuthBrowserLoginReasons
import dev.warp.mobile.keyboard.TerminalAction
import dev.warp.mobile.observability.MobileEventLogger
import dev.warp.mobile.session.SessionLaunchRequest
import java.util.concurrent.atomic.AtomicInteger

object RemoteSessionWebView {
    private val sequence = AtomicInteger()
    private val allowedSessionHosts = setOf(
        "app.warp.dev",
        "debug.warp.local",
        "localhost",
        "127.0.0.1",
    )
    private val allowedWarpHostSuffixes = setOf(".app.warp.dev")
    private val providerAuthHosts = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "apis.google.com",
        "github.com",
        "api.github.com",
        "oauth2.googleapis.com",
    )
    private val providerAuthHostSuffixes = setOf(
        ".github.com",
        ".githubassets.com",
        ".githubusercontent.com",
        ".google.com",
        ".gstatic.com",
        ".googleusercontent.com",
        ".googleapis.com",
    )
    private val authHandoffOrigins = setOf(
        "https://app.warp.dev",
        "https://debug.warp.local",
        "http://localhost",
        "http://127.0.0.1",
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        request: SessionLaunchRequest,
        logger: MobileEventLogger,
        authHandoffProvider: AuthHandoffProvider,
        onAuthRequired: (String) -> Unit,
    ): WebView {
        return WebView(context).apply {
            tag = request.loadUrl
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = mobileChromeUserAgent(settings.userAgentString)
            logger.event(
                "mobile_webview_user_agent_configured",
                mapOf("contains_webview_marker" to settings.userAgentString.contains("; wv").toString()),
            )
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(WarpHostBridge(logger), "WarpAndroidHost")
            addJavascriptInterface(
                WarpAuthHandoffBridge(authHandoffProvider, logger, onAuthRequired),
                "WarpAndroidAuthHandoff",
            )
            val authHandoffScript = WebViewAuthScripts.authHandoffScript()
            val injectAuthOnPageStart = installAuthHandoffScript(authHandoffScript, logger)
            webChromeClient = loggingChromeClient(logger, onAuthRequired)
            webViewClient = guardedWebViewClient(
                logger = logger,
                authHandoffScript = authHandoffScript,
                injectAuthOnPageStart = injectAuthOnPageStart,
                onAuthRequired = onAuthRequired,
            )
            loadRequest(request, logger)
        }
    }

    fun flushPersistentState(logger: MobileEventLogger, reason: String) {
        CookieManager.getInstance().flush()
        logger.event("mobile_webview_persistent_state_flushed", mapOf("reason" to reason))
    }

    fun update(webView: WebView, request: SessionLaunchRequest, logger: MobileEventLogger) {
        if (webView.tag == request.loadUrl) return
        webView.tag = request.loadUrl
        webView.loadRequest(request, logger)
    }

    fun loadAllowedUrl(webView: WebView?, rawUrl: String, logger: MobileEventLogger, reason: String) {
        if (webView == null) {
            logger.warn("mobile_webview_url_load_skipped", mapOf("reason" to "webview_not_ready"))
            return
        }
        val host = Uri.parse(rawUrl).host.orEmpty()
        if (!isAllowedHost(host)) {
            logger.warn("mobile_webview_url_load_blocked", mapOf("host" to host, "reason" to reason))
            return
        }
        logger.event("mobile_webview_url_load_requested", mapOf("host" to host, "reason" to reason))
        webView.loadUrl(rawUrl)
    }

    fun dispatchTerminalAction(webView: WebView?, action: TerminalAction, logger: MobileEventLogger) {
        if (webView == null) {
            logger.warn("mobile_bridge_message_rejected", mapOf("reason" to "webview_not_ready"))
            return
        }
        val sequenceId = "android-keyboard-${sequence.incrementAndGet()}"
        val message = action.toBridgeJson(sequenceId)
        val escaped = org.json.JSONObject.quote(message)
        logger.event(
            "mobile_bridge_message_sent",
            mapOf("sequence_id" to sequenceId, "kind" to "TERMINAL_ACTION"),
        )
        webView.evaluateJavascript(
            "window.warpAndroidBridge && window.warpAndroidBridge.receiveFromAndroid($escaped)",
            null,
        )
    }

    fun scrollFocusedInputIntoView(webView: WebView?, logger: MobileEventLogger, reason: String) {
        if (webView == null) {
            logger.warn("mobile_webview_focus_scroll_skipped", mapOf("reason" to "webview_not_ready"))
            return
        }
        webView.requestFocus()
        logger.event("mobile_webview_focus_scroll_requested", mapOf("reason" to reason))
        webView.evaluateJavascript(focusScrollScript(), null)
    }

    private fun WebView.loadRequest(request: SessionLaunchRequest, logger: MobileEventLogger) {
        logger.event("mobile_webview_load_started", mapOf("session_id_hash" to request.sessionIdHash))
        if (request.useFakePage) {
            loadDataWithBaseURL(request.loadUrl, fakeRemoteControlPage(), "text/html", "UTF-8", null)
        } else {
            loadUrl(request.loadUrl)
        }
    }

    private fun loggingChromeClient(logger: MobileEventLogger, onAuthRequired: (String) -> Unit): WebChromeClient {
        return object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                logger.event(
                    "mobile_webview_popup_requested",
                    mapOf("is_dialog" to isDialog.toString(), "is_user_gesture" to isUserGesture.toString()),
                )
                val popupView = WebView(view.context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = mobileChromeUserAgent(settings.userAgentString)
                    @Suppress("DEPRECATION")
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(false)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = popupRedirectClient(view, logger, onAuthRequired)
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = popupView
                resultMsg.sendToTarget()
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                logger.event(
                    "mobile_webview_console",
                    mapOf(
                        "level" to consoleMessage.messageLevel().name,
                        "line" to consoleMessage.lineNumber().toString(),
                        "message" to sanitizeLogText(consoleMessage.message()),
                        "source" to sanitizeLogText(consoleMessage.sourceId()),
                    ),
                )
                return true
            }
        }
    }

    private fun popupRedirectClient(
        targetView: WebView,
        logger: MobileEventLogger,
        onAuthRequired: (String) -> Unit,
    ): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return redirectPopup(targetView, request.url, logger, onAuthRequired)
            }

            override fun onPageFinished(view: WebView, url: String) {
                val uri = Uri.parse(url)
                if (redirectPopup(targetView, uri, logger, onAuthRequired)) {
                    view.destroy()
                }
            }
        }
    }

    private fun redirectPopup(
        targetView: WebView,
        uri: Uri,
        logger: MobileEventLogger,
        onAuthRequired: (String) -> Unit,
    ): Boolean {
        val url = uri.toString()
        val host = uri.host.orEmpty()
        if (host.isBlank() || url == "about:blank") {
            return false
        }
        if (isAuthNavigation(uri)) {
            logger.event("mobile_webview_auth_popup_externalized", mapOf("host" to host))
            onAuthRequired(AuthBrowserLoginReasons.WebViewAuthPopup)
            return true
        }
        if (!isAllowedHost(host)) {
            logger.warn("mobile_webview_popup_url_blocked", mapOf("host" to host))
            return true
        }
        logger.event("mobile_webview_popup_url_accepted", mapOf("host" to host))
        targetView.loadUrl(url)
        return true
    }

    private fun guardedWebViewClient(
        logger: MobileEventLogger,
        authHandoffScript: String,
        injectAuthOnPageStart: Boolean,
        onAuthRequired: (String) -> Unit,
    ): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                val uri = Uri.parse(url)
                val host = uri.host.orEmpty()
                logger.event("mobile_webview_page_started", mapOf("host" to host, "path" to uri.path.orEmpty().take(96)))
                if (injectAuthOnPageStart && isAllowedHost(host)) {
                    view.evaluateJavascript(authHandoffScript, null)
                    logger.event("mobile_auth_handoff_script_injected", mapOf("mode" to "page_started"))
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                if (request.isForMainFrame && isAuthNavigation(request.url)) {
                    logger.event("mobile_webview_auth_navigation_externalized", mapOf("host" to host))
                    onAuthRequired(AuthBrowserLoginReasons.WebViewAuthNavigation)
                    return true
                }
                val allowed = isAllowedHost(host)
                if (!allowed) {
                    logger.warn(
                        "mobile_webview_external_url_blocked",
                        mapOf(
                            "host" to host,
                            "is_main_frame" to request.isForMainFrame.toString(),
                        ),
                    )
                }
                return !allowed
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                logger.warn(
                    "mobile_webview_resource_error",
                    mapOf(
                        "host" to request.url.host.orEmpty(),
                        "is_main_frame" to request.isForMainFrame.toString(),
                        "path" to request.url.path.orEmpty().take(96),
                        "code" to error.errorCode.toString(),
                        "description" to sanitizeLogText(error.description.toString()),
                    ),
                )
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                logger.warn(
                    "mobile_webview_http_error",
                    mapOf(
                        "host" to request.url.host.orEmpty(),
                        "is_main_frame" to request.isForMainFrame.toString(),
                        "path" to request.url.path.orEmpty().take(96),
                        "status" to errorResponse.statusCode.toString(),
                    ),
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                val uri = Uri.parse(url)
                logger.event("mobile_webview_page_finished", mapOf("host" to uri.host.orEmpty(), "path" to uri.path.orEmpty().take(96)))
                flushPersistentState(logger, "page_finished")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                logger.warn("mobile_webview_ssl_blocked")
                handler.cancel()
            }
        }
    }

    private fun isAllowedHost(host: String): Boolean {
        return host in allowedSessionHosts ||
            allowedWarpHostSuffixes.any { suffix -> host.endsWith(suffix) }
    }

    private fun isAuthNavigation(uri: Uri): Boolean {
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        return isProviderAuthHost(host) ||
            (host == "app.warp.dev" && (path.startsWith("/login") || path.startsWith("/signup")))
    }

    private fun isProviderAuthHost(host: String): Boolean {
        return host in providerAuthHosts ||
            providerAuthHostSuffixes.any { suffix -> host.endsWith(suffix) }
    }

    private fun WebView.installAuthHandoffScript(script: String, logger: MobileEventLogger): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            logger.warn("mobile_auth_handoff_document_start_unavailable")
            return true
        }
        return try {
            WebViewCompat.addDocumentStartJavaScript(this, script, authHandoffOrigins)
            logger.event("mobile_auth_handoff_script_installed", mapOf("mode" to "document_start"))
            false
        } catch (_: RuntimeException) {
            logger.warn("mobile_auth_handoff_script_install_failed")
            true
        }
    }

    private fun focusScrollScript(): String {
        return """
            (function() {
              const selectors = [
                'textarea.xterm-helper-textarea',
                '.xterm-helper-textarea',
                '[contenteditable="true"]',
                'textarea',
                'input'
              ];
              const nodes = [];
              if (document.activeElement) nodes.push(document.activeElement);
              for (const selector of selectors) {
                const node = document.querySelector(selector);
                if (node) nodes.push(node);
              }
              for (const node of nodes) {
                if (!node || node === document.body || node === document.documentElement) continue;
                try {
                  if (typeof node.focus === 'function') node.focus({preventScroll: false});
                  if (typeof node.scrollIntoView === 'function') {
                    node.scrollIntoView({block: 'center', inline: 'nearest', behavior: 'instant'});
                    return true;
                  }
                } catch (_) {}
              }
              window.scrollBy(0, 1);
              window.scrollBy(0, -1);
              return false;
            })();
        """.trimIndent()
    }

    private fun mobileChromeUserAgent(defaultUserAgent: String): String {
        return defaultUserAgent
            .replace("; wv", "")
            .replace(" Version/4.0", "")
    }

    private fun sanitizeLogText(value: String): String {
        return value
            .replace(Regex("refresh_token=[^&\\s]+"), "refresh_token=REDACTED")
            .replace(Regex("state=[^&\\s]+"), "state=REDACTED")
            .take(512)
    }

    private fun fakeRemoteControlPage(): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <style>
                body { margin:0; background:#000; color:#fff; font-family:monospace; }
                #terminal { padding:16px; white-space:pre-wrap; }
                .muted { color:#999; }
              </style>
            </head>
            <body>
              <div id="terminal">Warp mobile fake remote session\n<span class="muted">Waiting for native keyboard input...</span></div>
              <script>
                window.warpAndroidBridge = {
                  receiveFromAndroid(message) {
                    const node = document.getElementById('terminal');
                    node.textContent += '\n' + message;
                    if (window.WarpAndroidHost) {
                      window.WarpAndroidHost.emitWarpEvent(JSON.stringify({
                        kind: 'TERMINAL_ACTION_ACK',
                        sequenceId: JSON.parse(message).sequenceId
                      }));
                    }
                  }
                };
                if (window.WarpAndroidHost) {
                  window.WarpAndroidHost.emitWarpEvent(JSON.stringify({kind:'BRIDGE_READY'}));
                  window.WarpAndroidHost.emitWarpEvent(JSON.stringify({
                    kind:'SESSION_INPUT_CAPABILITY_CHANGED',
                    canSendInput:true
                  }));
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
