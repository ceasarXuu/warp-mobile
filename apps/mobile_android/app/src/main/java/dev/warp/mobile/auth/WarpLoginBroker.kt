package dev.warp.mobile.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import dev.warp.mobile.observability.MobileEventLogger
import java.security.SecureRandom

class WarpLoginBroker(context: Context) : AuthHandoffProvider {
    private val appContext = context.applicationContext
    private val tokenStore = AuthTokenStore(appContext)
    private val prefs = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    override fun refreshTokenForHandoff(): String? = tokenStore.refreshTokenForHandoff()

    fun hasRefreshToken(): Boolean = tokenStore.hasRefreshToken()

    fun beginBrowserLogin(activity: Activity, logger: MobileEventLogger) {
        val state = generateState()
        prefs.edit().putString(PendingStateKey, state).apply()
        val url = WarpAuthRedirectParser.buildLoginUrl(state)
        logger.event("mobile_auth_browser_login_started")
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                },
            )
        } catch (_: ActivityNotFoundException) {
            logger.warn("mobile_auth_browser_login_failed", mapOf("reason" to "no_browser"))
        }
    }

    fun isAuthRedirect(uri: Uri): Boolean {
        return uri.scheme == WarpAuthRedirectParser.RedirectScheme &&
            uri.host == WarpAuthRedirectParser.RedirectHost &&
            uri.path == WarpAuthRedirectParser.RedirectPath
    }

    fun handleAuthRedirect(uri: Uri, logger: MobileEventLogger): Boolean {
        val redirect = try {
            WarpAuthRedirectParser.parse(uri.toString())
        } catch (error: WarpAuthRedirectException) {
            logger.warn("mobile_auth_redirect_rejected", mapOf("reason" to error.reason))
            return false
        }

        val pendingState = prefs.getString(PendingStateKey, null)
        if (pendingState == null || redirect.state != pendingState) {
            logger.warn("mobile_auth_redirect_rejected", mapOf("reason" to "state_mismatch"))
            return false
        }

        tokenStore.saveRefreshToken(redirect.refreshToken)
        prefs.edit().remove(PendingStateKey).apply()
        logger.event(
            "mobile_auth_redirect_accepted",
            mapOf("user_uid_present" to (redirect.userUid != null).toString()),
        )
        return true
    }

    private fun generateState(): String {
        val bytes = ByteArray(StateByteLength)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private companion object {
        const val PrefsName = "warp_mobile_auth"
        const val PendingStateKey = "pending_state"
        const val StateByteLength = 24
    }
}
