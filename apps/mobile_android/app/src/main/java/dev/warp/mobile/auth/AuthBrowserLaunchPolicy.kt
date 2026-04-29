package dev.warp.mobile.auth

object AuthBrowserLoginReasons {
    const val User = "user"
    const val AutoNoToken = "auto_no_token"
    const val RecentSignInRequired = "recent_sign_in_required"
    const val WebViewAuthNavigation = "webview_auth_navigation"
    const val WebViewAuthPopup = "webview_auth_popup"

    fun normalize(reason: String): String = reason.trim().take(80).ifBlank { User }
}

data class AuthBrowserLaunchDecision(
    val shouldStartBrowser: Boolean,
    val forceFreshCredential: Boolean,
    val suppressionReason: String? = null,
    val pendingReplacementReason: String? = null,
)

object AuthBrowserLaunchPolicy {
    const val DuplicateLaunchWindowMs = 2_000L
    const val FreshRedirectLoopWindowMs = 60_000L
    const val PendingBrowserLoginWindowMs = 120_000L

    fun decide(
        reason: String,
        hasRefreshToken: Boolean,
        nowElapsedMillis: Long,
        lastBrowserLoginStartedAtElapsedMillis: Long,
        lastAuthRedirectAcceptedAtElapsedMillis: Long,
        lastBrowserLoginForcedFresh: Boolean,
        hasPendingBrowserLogin: Boolean = false,
        pendingBrowserLoginStartedAtElapsedMillis: Long = 0L,
    ): AuthBrowserLaunchDecision {
        val normalizedReason = AuthBrowserLoginReasons.normalize(reason)
        val forceFreshCredential = shouldForceFreshCredential(normalizedReason, hasRefreshToken)
        val pendingLoginStillActive = hasPendingBrowserLogin &&
            isWithinWindow(nowElapsedMillis, pendingBrowserLoginStartedAtElapsedMillis, PendingBrowserLoginWindowMs)
        if (pendingLoginStillActive) {
            return AuthBrowserLaunchDecision(
                shouldStartBrowser = false,
                forceFreshCredential = forceFreshCredential,
                suppressionReason = "pending_browser_login",
            )
        }
        val pendingReplacementReason = if (hasPendingBrowserLogin) {
            "stale_pending_browser_login"
        } else {
            null
        }

        if (
            normalizedReason != AuthBrowserLoginReasons.User &&
            isWithinWindow(nowElapsedMillis, lastBrowserLoginStartedAtElapsedMillis, DuplicateLaunchWindowMs)
        ) {
            return AuthBrowserLaunchDecision(
                shouldStartBrowser = false,
                forceFreshCredential = forceFreshCredential,
                suppressionReason = "duplicate_launch",
            )
        }

        if (
            normalizedReason == AuthBrowserLoginReasons.RecentSignInRequired &&
            lastBrowserLoginForcedFresh &&
            isWithinWindow(nowElapsedMillis, lastAuthRedirectAcceptedAtElapsedMillis, FreshRedirectLoopWindowMs)
        ) {
            return AuthBrowserLaunchDecision(
                shouldStartBrowser = false,
                forceFreshCredential = true,
                suppressionReason = "fresh_redirect_still_recent_sign_in_required",
            )
        }

        return AuthBrowserLaunchDecision(
            shouldStartBrowser = true,
            forceFreshCredential = forceFreshCredential,
            pendingReplacementReason = pendingReplacementReason,
        )
    }

    private fun shouldForceFreshCredential(reason: String, hasRefreshToken: Boolean): Boolean {
        return reason == AuthBrowserLoginReasons.RecentSignInRequired ||
            (hasRefreshToken && reason == AuthBrowserLoginReasons.User) ||
            (hasRefreshToken && reason == AuthBrowserLoginReasons.WebViewAuthNavigation) ||
            (hasRefreshToken && reason == AuthBrowserLoginReasons.WebViewAuthPopup)
    }

    private fun isWithinWindow(nowElapsedMillis: Long, previousElapsedMillis: Long, windowMillis: Long): Boolean {
        return previousElapsedMillis > 0 &&
            nowElapsedMillis >= previousElapsedMillis &&
            nowElapsedMillis - previousElapsedMillis <= windowMillis
    }
}
