package dev.warp.mobile.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import dev.warp.mobile.auth.AuthHandoffProvider
import dev.warp.mobile.observability.MobileEventLogger

class WarpHostBridge(private val logger: MobileEventLogger) {
    @JavascriptInterface
    fun emitWarpEvent(json: String) {
        logger.event("mobile_bridge_message_received", mapOf("payload_size" to json.length.toString()))
    }
}

class WarpAuthHandoffBridge(
    private val provider: AuthHandoffProvider,
    private val logger: MobileEventLogger,
    private val onAuthRequired: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun refreshToken(): String? {
        val token = provider.refreshTokenForHandoff()
        logger.event("mobile_auth_handoff_token_requested", mapOf("token_present" to (token != null).toString()))
        return token
    }

    @JavascriptInterface
    fun updateRefreshToken(token: String) {
        provider.saveRefreshTokenFromHandoff(token)
        logger.event("mobile_auth_handoff_token_updated")
    }

    @JavascriptInterface
    fun requestBrowserLogin(reason: String) {
        logger.warn("mobile_auth_handoff_browser_login_requested", mapOf("reason" to reason.take(80)))
        mainHandler.post { onAuthRequired() }
    }
}
