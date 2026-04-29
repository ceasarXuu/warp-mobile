package dev.warp.mobile.tabs

import dev.warp.mobile.session.LaunchSource
import dev.warp.mobile.session.SessionLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteTabDeduplicatorTest {
    @Test
    fun findsExistingTabForSameNormalizedLink() {
        val existing = tab(
            id = "tab-1",
            rawUrl = "https://app.warp.dev/session/00000000-0000-0000-0000-000000000001?pwd=secret",
        )
        val request = SessionLinkParser.parse(
            "HTTPS://APP.WARP.DEV/session/00000000-0000-0000-0000-000000000001/?pwd=secret",
            LaunchSource.USER_CREATED,
        )

        val duplicate = RemoteTabDeduplicator.findExistingTab(listOf(existing), request)

        assertEquals("tab-1", duplicate?.id)
    }

    @Test
    fun keepsDifferentQueryValuesAsDifferentLinks() {
        val existing = tab(
            id = "tab-1",
            rawUrl = "https://app.warp.dev/session/00000000-0000-0000-0000-000000000001?pwd=secret-a",
        )
        val request = SessionLinkParser.parse(
            "https://app.warp.dev/session/00000000-0000-0000-0000-000000000001?pwd=secret-b",
            LaunchSource.USER_CREATED,
        )

        assertNull(RemoteTabDeduplicator.findExistingTab(listOf(existing), request))
    }

    @Test
    fun collapsesRestoredDuplicatesAndMapsSelectedDuplicateToKeptTab() {
        val snapshot = RemoteTabSnapshot(
            tabs = listOf(
                tab("tab-1", "https://app.warp.dev/session/00000000-0000-0000-0000-000000000001"),
                tab("tab-2", "https://app.warp.dev/session/00000000-0000-0000-0000-000000000002"),
                tab("tab-3", "https://app.warp.dev/session/00000000-0000-0000-0000-000000000001/"),
            ),
            selectedTabId = "tab-3",
        )

        val collapsed = RemoteTabDeduplicator.collapse(snapshot)

        assertEquals(listOf("tab-1", "tab-2"), collapsed.tabs.map { tab -> tab.id })
        assertEquals("tab-1", collapsed.selectedTabId)
    }

    private fun tab(id: String, rawUrl: String): RemoteTab {
        return RemoteTab(
            id = id,
            request = SessionLinkParser.parse(rawUrl, LaunchSource.RESTORE),
            createdAtEpochMillis = id.takeLast(1).toLong(),
        )
    }
}
