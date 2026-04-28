package dev.warp.mobile.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private val allowedAuthHosts = setOf("accounts.google.com")

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        request: SessionLaunchRequest,
        logger: MobileEventLogger,
    ): WebView {
        return WebView(context).apply {
            tag = request.loadUrl
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(WarpHostBridge(logger), "WarpAndroidHost")
            webChromeClient = loggingChromeClient(logger)
            webViewClient = guardedWebViewClient(logger)
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

    private fun WebView.loadRequest(request: SessionLaunchRequest, logger: MobileEventLogger) {
        logger.event("mobile_webview_load_started", mapOf("session_id_hash" to request.sessionIdHash))
        if (request.useFakePage) {
            loadDataWithBaseURL(request.loadUrl, fakeRemoteControlPage(), "text/html", "UTF-8", null)
        } else {
            loadUrl(request.loadUrl)
        }
    }

    private fun loggingChromeClient(logger: MobileEventLogger): WebChromeClient {
        return object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                logger.event(
                    "mobile_webview_console",
                    mapOf(
                        "level" to consoleMessage.messageLevel().name,
                        "line" to consoleMessage.lineNumber().toString(),
                    ),
                )
                return true
            }
        }
    }

    private fun guardedWebViewClient(logger: MobileEventLogger): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
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
            allowedWarpHostSuffixes.any { suffix -> host.endsWith(suffix) } ||
            host in allowedAuthHosts
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

class WarpHostBridge(private val logger: MobileEventLogger) {
    @JavascriptInterface
    fun emitWarpEvent(json: String) {
        logger.event("mobile_bridge_message_received", mapOf("payload_size" to json.length.toString()))
    }
}
