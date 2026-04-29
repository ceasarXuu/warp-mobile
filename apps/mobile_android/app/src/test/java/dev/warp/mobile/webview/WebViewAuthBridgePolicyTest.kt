package dev.warp.mobile.webview

import dev.warp.mobile.session.LaunchSource
import dev.warp.mobile.session.SessionLaunchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewAuthBridgePolicyTest {
    @Test
    fun exposesTokenBridgeOnlyForHostedWarpAppSession() {
        assertTrue(WebViewAuthBridgePolicy.shouldExposeTokenBridge(request("https://app.warp.dev/session/$SessionId")))
        assertFalse(WebViewAuthBridgePolicy.shouldExposeTokenBridge(request("https://debug.warp.local/session/$SessionId", fake = true)))
        assertFalse(WebViewAuthBridgePolicy.shouldExposeTokenBridge(request("http://localhost/session/$SessionId")))
        assertFalse(WebViewAuthBridgePolicy.shouldExposeTokenBridge(request("https://preview.app.warp.dev/session/$SessionId")))
    }

    @Test
    fun documentStartScriptIsScopedToHostedWarpAppOrigin() {
        assertEquals(setOf("https://app.warp.dev"), WebViewAuthBridgePolicy.authHandoffOrigins)
    }

    @Test
    fun tokenBridgeModeDoesNotAllowDebugOrLocalNavigationHosts() {
        assertTrue(WebViewAuthBridgePolicy.isAllowedNavigationHost("app.warp.dev", tokenBridgeExposed = true))
        assertFalse(WebViewAuthBridgePolicy.isAllowedNavigationHost("debug.warp.local", tokenBridgeExposed = true))
        assertFalse(WebViewAuthBridgePolicy.isAllowedNavigationHost("localhost", tokenBridgeExposed = true))
        assertFalse(WebViewAuthBridgePolicy.isAllowedNavigationHost("127.0.0.1", tokenBridgeExposed = true))
    }

    @Test
    fun noTokenBridgeModeCanStillLoadDebugFakeSessions() {
        assertTrue(WebViewAuthBridgePolicy.isAllowedNavigationHost("debug.warp.local", tokenBridgeExposed = false))
        assertTrue(WebViewAuthBridgePolicy.isAllowedNavigationHost("localhost", tokenBridgeExposed = false))
        assertTrue(WebViewAuthBridgePolicy.isAllowedNavigationHost("127.0.0.1", tokenBridgeExposed = false))
    }

    private fun request(url: String, fake: Boolean = false): SessionLaunchRequest {
        return SessionLaunchRequest(
            originalUrlHash = "hash",
            loadUrl = url,
            redactedUrl = url,
            sessionId = SessionId,
            sessionIdHash = "session-hash",
            queryKeys = emptySet(),
            source = LaunchSource.COLD_START,
            useFakePage = fake,
        )
    }

    private companion object {
        const val SessionId = "00000000-0000-0000-0000-000000000001"
    }
}
