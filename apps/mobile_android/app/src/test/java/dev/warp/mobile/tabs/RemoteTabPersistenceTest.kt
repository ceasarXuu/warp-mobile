package dev.warp.mobile.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteTabPersistenceTest {
    @Test
    fun roundTripsStoredTabs() {
        val snapshot = StoredRemoteTabs(
            tabs = listOf(
                StoredRemoteTab(
                    id = "tab-1",
                    rawUrl = "https://app.warp.dev/session/00000000-0000-0000-0000-000000000001",
                    createdAtEpochMillis = 123L,
                ),
                StoredRemoteTab(
                    id = "tab-2",
                    rawUrl = "https://app.warp.dev/session/00000000-0000-0000-0000-000000000002",
                    createdAtEpochMillis = 456L,
                ),
            ),
            selectedTabId = "tab-2",
        )

        val restored = RemoteTabPersistence.decode(RemoteTabPersistence.encode(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun ignoresMalformedTabEntries() {
        val restored = RemoteTabPersistence.decode(
            """
            {
              "schema": 1,
              "selectedTabId": "",
              "tabs": [
                {"id": "", "rawUrl": "https://app.warp.dev/session/00000000-0000-0000-0000-000000000001"},
                {"id": "tab-1", "createdAtEpochMillis": 100},
                {"id": "tab-2", "rawUrl": "https://app.warp.dev/session/00000000-0000-0000-0000-000000000002"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, restored.tabs.size)
        assertEquals("tab-2", restored.tabs.single().id)
        assertNull(restored.selectedTabId)
    }
}
