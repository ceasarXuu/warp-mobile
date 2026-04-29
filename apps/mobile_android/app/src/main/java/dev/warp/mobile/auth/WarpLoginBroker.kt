package dev.warp.mobile.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import dev.warp.mobile.observability.MobileEventLogger
import java.security.SecureRandom

class WarpLoginBroker(context: Context) : AuthHandoffProvider {
    private val appContext = context.applicationContext
    private val tokenStore = AuthTokenStore(appContext)
    private val prefs = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    override fun refreshTokenForHandoff(): String? = tokenStore.refreshTokenForHandoff()

    override fun saveRefreshTokenFromHandoff(token: String) {
        tokenStore.saveRefreshTokenFromHandoff(token)
    }

    fun hasRefreshToken(): Boolean = tokenStore.hasRefreshToken()

    fun shouldAttemptEmbeddedSession(): Boolean {
        return tokenStore.hasRefreshToken() && !prefs.getBoolean(EmbeddedAuthGateRequiredKey, false)
    }

    fun beginBrowserLogin(
        activity: Activity,
        logger: MobileEventLogger,
        reason: String = AuthBrowserLoginReasons.User,
    ): Boolean {
        val normalizedReason = AuthBrowserLoginReasons.normalize(reason)
        setEmbeddedAuthGateRequired(true, logger, normalizedReason)
        val now = SystemClock.elapsedRealtime()
        val pendingState = prefs.getString(PendingStateKey, null)
        val decision = AuthBrowserLaunchPolicy.decide(
            reason = normalizedReason,
            hasRefreshToken = tokenStore.hasRefreshToken(),
            nowElapsedMillis = now,
            lastBrowserLoginStartedAtElapsedMillis = prefs.getLong(LastBrowserLoginStartedAtKey, 0L),
            lastAuthRedirectAcceptedAtElapsedMillis = prefs.getLong(LastAuthRedirectAcceptedAtKey, 0L),
            lastBrowserLoginForcedFresh = prefs.getBoolean(LastBrowserLoginForcedFreshKey, false),
            hasPendingBrowserLogin = pendingState != null,
            pendingBrowserLoginStartedAtElapsedMillis = prefs.getLong(PendingBrowserLoginStartedAtKey, 0L),
        )
        if (!decision.shouldStartBrowser) {
            logger.warn(
                "mobile_auth_browser_login_suppressed",
                mapOf(
                    "reason" to normalizedReason,
                    "policy" to decision.suppressionReason.orEmpty(),
                    "force_fresh" to decision.forceFreshCredential.toString(),
                ),
            )
            return false
        }
        decision.pendingReplacementReason?.let { replacementReason ->
            logger.warn(
                "mobile_auth_pending_browser_login_replaced",
                mapOf(
                    "reason" to normalizedReason,
                    "replacement_reason" to replacementReason,
                ),
            )
        }

        val state = generateState()
        prefs.edit()
            .putString(PendingStateKey, state)
            .putLong(PendingBrowserLoginStartedAtKey, now)
            .putLong(LastBrowserLoginStartedAtKey, now)
            .putString(LastBrowserLoginReasonKey, normalizedReason)
            .putBoolean(LastBrowserLoginForcedFreshKey, decision.forceFreshCredential)
            .apply()
        val url = WarpAuthRedirectParser.buildLoginUrl(state, decision.forceFreshCredential)
        logger.event(
            "mobile_auth_browser_login_started",
            mapOf(
                "reason" to normalizedReason,
                "force_fresh" to decision.forceFreshCredential.toString(),
            ),
        )
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                },
            )
        } catch (_: ActivityNotFoundException) {
            prefs.edit()
                .remove(PendingStateKey)
                .remove(PendingBrowserLoginStartedAtKey)
                .apply()
            logger.warn("mobile_auth_browser_login_failed", mapOf("reason" to "no_browser"))
            return false
        }
        return true
    }

    fun isAuthRedirect(uri: Uri): Boolean {
        return uri.scheme in WarpAuthRedirectParser.SupportedRedirectSchemes &&
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
        val wasEmbeddedGateRequired = prefs.getBoolean(EmbeddedAuthGateRequiredKey, false)
        prefs.edit()
            .remove(PendingStateKey)
            .remove(PendingBrowserLoginStartedAtKey)
            .putLong(LastAuthRedirectAcceptedAtKey, SystemClock.elapsedRealtime())
            .putBoolean(EmbeddedAuthGateRequiredKey, false)
            .apply()
        if (wasEmbeddedGateRequired) {
            logger.event(
                "mobile_auth_embedded_gate_changed",
                mapOf("required" to "false", "reason" to "auth_redirect_accepted"),
            )
        }
        logger.event(
            "mobile_auth_redirect_accepted",
            mapOf(
                "user_uid_present" to (redirect.userUid != null).toString(),
                "force_fresh" to prefs.getBoolean(LastBrowserLoginForcedFreshKey, false).toString(),
            ),
        )
        return true
    }

    private fun setEmbeddedAuthGateRequired(required: Boolean, logger: MobileEventLogger, reason: String) {
        if (prefs.getBoolean(EmbeddedAuthGateRequiredKey, false) == required) return
        prefs.edit().putBoolean(EmbeddedAuthGateRequiredKey, required).apply()
        logger.event(
            "mobile_auth_embedded_gate_changed",
            mapOf("required" to required.toString(), "reason" to reason),
        )
    }

    private fun generateState(): String {
        val bytes = ByteArray(StateByteLength)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private companion object {
        const val PrefsName = "warp_mobile_auth"
        const val PendingStateKey = "pending_state"
        const val PendingBrowserLoginStartedAtKey = "pending_browser_login_started_at"
        const val LastBrowserLoginStartedAtKey = "last_browser_login_started_at"
        const val LastBrowserLoginReasonKey = "last_browser_login_reason"
        const val LastBrowserLoginForcedFreshKey = "last_browser_login_forced_fresh"
        const val LastAuthRedirectAcceptedAtKey = "last_auth_redirect_accepted_at"
        const val EmbeddedAuthGateRequiredKey = "embedded_auth_gate_required"
        const val StateByteLength = 24
    }
}
