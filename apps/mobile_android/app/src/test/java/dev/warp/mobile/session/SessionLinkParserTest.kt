package dev.warp.mobile.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLinkParserTest {
    @Test
    fun parsesValidWarpSessionUrl() {
        val request = SessionLinkParser.parse(
            "https://app.warp.dev/session/00000000-0000-0000-0000-000000000001?pwd=secret&mode=view",
            LaunchSource.COLD_START,
        )

        assertEquals("00000000-0000-0000-0000-000000000001", request.sessionId)
        assertEquals(setOf("pwd", "mode"), request.queryKeys)
        assertTrue(request.redactedUrl.contains("pwd=REDACTED"))
        assertTrue(request.redactedUrl.contains("mode=view"))
        assertFalse(request.useFakePage)
    }

    @Test
    fun parsesDebugFakeSessionUrl() {
        val request = SessionLinkParser.parse(
            "https://debug.warp.local/session/00000000-0000-0000-0000-000000000000",
            LaunchSource.DEBUG_DEMO,
        )

        assertTrue(request.useFakePage)
    }

    @Test
    fun rejectsUnsupportedHost() {
        val error = runCatching {
            SessionLinkParser.parse(
                "https://example.com/session/00000000-0000-0000-0000-000000000001",
                LaunchSource.COLD_START,
            )
        }.exceptionOrNull() as SessionLinkParseException

        assertEquals("unsupported_origin", error.reason)
    }

    @Test
    fun rejectsInvalidSessionId() {
        val error = runCatching {
            SessionLinkParser.parse("https://app.warp.dev/session/not-a-uuid", LaunchSource.COLD_START)
        }.exceptionOrNull() as SessionLinkParseException

        assertEquals("invalid_session_id", error.reason)
    }
}
