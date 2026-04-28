package dev.warp.mobile.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
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
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            addJavascriptInterface(WarpHostBridge(logger), "WarpAndroidHost")
            webChromeClient = loggingChromeClient(logger)
            webViewClient = guardedWebViewClient(logger)
            loadRequest(request, logger)
        }
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
                    mapOf("level" to consoleMessage.messageLevel().name),
                )
                return true
            }
        }
    }

    private fun guardedWebViewClient(logger: MobileEventLogger): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                val allowed = host in setOf("app.warp.dev", "debug.warp.local", "localhost", "127.0.0.1")
                if (!allowed) {
                    logger.warn("mobile_webview_external_url_blocked", mapOf("host" to host))
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
                    mapOf("status" to errorResponse.statusCode.toString()),
                )
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                logger.warn("mobile_webview_ssl_blocked")
                handler.cancel()
            }
        }
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
