package dev.warp.mobile.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthBrowserLaunchPolicyTest {
    @Test
    fun startsStandardBrowserLoginWhenNoTokenExists() {
        val decision = AuthBrowserLaunchPolicy.decide(
            reason = AuthBrowserLoginReasons.AutoNoToken,
            hasRefreshToken = false,
            nowElapsedMillis = 10_000L,
            lastBrowserLoginStartedAtElapsedMillis = 0L,
            lastAuthRedirectAcceptedAtElapsedMillis = 0L,
            lastBrowserLoginForcedFresh = false,
        )

        assertTrue(decision.shouldStartBrowser)
        assertFalse(decision.forceFreshCredential)
    }

    @Test
    fun recentSignInRequiredStartsFreshBrowserLoginAfterStandardRedirect() {
        val decision = AuthBrowserLaunchPolicy.decide(
            reason = AuthBrowserLoginReasons.RecentSignInRequired,
            hasRefreshToken = true,
            nowElapsedMillis = 12_000L,
            lastBrowserLoginStartedAtElapsedMillis = 8_000L,
            lastAuthRedirectAcceptedAtElapsedMillis = 11_000L,
            lastBrowserLoginForcedFresh = false,
        )

        assertTrue(decision.shouldStartBrowser)
        assertTrue(decision.forceFreshCredential)
    }

    @Test
    fun suppressesLoopWhenFreshRedirectStillNeedsRecentSignIn() {
        val decision = AuthBrowserLaunchPolicy.decide(
            reason = AuthBrowserLoginReasons.RecentSignInRequired,
            hasRefreshToken = true,
            nowElapsedMillis = 30_000L,
            lastBrowserLoginStartedAtElapsedMillis = 20_000L,
            lastAuthRedirectAcceptedAtElapsedMillis = 29_000L,
            lastBrowserLoginForcedFresh = true,
        )

        assertFalse(decision.shouldStartBrowser)
        assertTrue(decision.forceFreshCredential)
        assertEquals("fresh_redirect_still_recent_sign_in_required", decision.suppressionReason)
    }

    @Test
    fun suppressesDuplicateAutomaticLaunches() {
        val decision = AuthBrowserLaunchPolicy.decide(
            reason = AuthBrowserLoginReasons.WebViewAuthNavigation,
            hasRefreshToken = false,
            nowElapsedMillis = 10_500L,
            lastBrowserLoginStartedAtElapsedMillis = 10_000L,
            lastAuthRedirectAcceptedAtElapsedMillis = 0L,
            lastBrowserLoginForcedFresh = false,
        )

        assertFalse(decision.shouldStartBrowser)
        assertEquals("duplicate_launch", decision.suppressionReason)
    }
}
