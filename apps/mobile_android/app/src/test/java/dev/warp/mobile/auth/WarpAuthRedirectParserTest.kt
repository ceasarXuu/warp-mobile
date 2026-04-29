package dev.warp.mobile.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarpAuthRedirectParserTest {
    @Test
    fun buildsRemoteLoginUrlWithMobileRedirectScheme() {
        val loginUrl = WarpAuthRedirectParser.buildLoginUrl("state value")

        assertTrue(loginUrl.startsWith("https://app.warp.dev/login/remote?"))
        assertTrue(loginUrl.contains("scheme=warposs"))
        assertTrue(loginUrl.contains("state=state+value"))
    }

    @Test
    fun buildsFreshRemoteLoginUrlWhenRecentSignInIsRequired() {
        val loginUrl = WarpAuthRedirectParser.buildLoginUrl("state value", forceFreshCredential = true)

        assertTrue(loginUrl.startsWith("https://app.warp.dev/login_options/remote?"))
        assertTrue(loginUrl.contains("scheme=warposs"))
        assertTrue(loginUrl.contains("state=state+value"))
    }

    @Test
    fun parsesValidDesktopRedirect() {
        val redirect = WarpAuthRedirectParser.parse(
            "warposs://auth/desktop_redirect?refresh_token=token-value&state=pending&user_uid=user-1",
        )

        assertEquals("token-value", redirect.refreshToken)
        assertEquals("pending", redirect.state)
        assertEquals("user-1", redirect.userUid)
    }

    @Test
    fun rejectsMissingRefreshToken() {
        val error = runCatching {
            WarpAuthRedirectParser.parse("warposs://auth/desktop_redirect?state=pending")
        }.exceptionOrNull() as WarpAuthRedirectException

        assertEquals("missing_refresh_token", error.reason)
    }

    @Test
    fun rejectsWrongRedirectOrigin() {
        val error = runCatching {
            WarpAuthRedirectParser.parse("https://app.warp.dev/desktop_redirect?refresh_token=token")
        }.exceptionOrNull() as WarpAuthRedirectException

        assertEquals("unsupported_scheme", error.reason)
    }
}
