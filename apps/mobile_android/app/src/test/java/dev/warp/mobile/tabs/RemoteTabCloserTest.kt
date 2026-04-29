package dev.warp.mobile.tabs

import dev.warp.mobile.session.LaunchSource
import dev.warp.mobile.session.SessionLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteTabCloserTest {
    @Test
    fun closingUnselectedTabKeepsCurrentSelection() {
        val snapshot = RemoteTabSnapshot(listOf(tab("a", 1), tab("b", 2), tab("c", 3)), "b")

        val next = RemoteTabCloser.close(snapshot, "a")

        assertEquals(listOf("b", "c"), next?.tabs?.map { tab -> tab.id })
        assertEquals("b", next?.selectedTabId)
    }

    @Test
    fun closingSelectedTabSelectsNextNeighbor() {
        val snapshot = RemoteTabSnapshot(listOf(tab("a", 1), tab("b", 2), tab("c", 3)), "b")

        val next = RemoteTabCloser.close(snapshot, "b")

        assertEquals(listOf("a", "c"), next?.tabs?.map { tab -> tab.id })
        assertEquals("c", next?.selectedTabId)
    }

    @Test
    fun closingSelectedLastTabSelectsPreviousNeighbor() {
        val snapshot = RemoteTabSnapshot(listOf(tab("a", 1), tab("b", 2)), "b")

        val next = RemoteTabCloser.close(snapshot, "b")

        assertEquals(listOf("a"), next?.tabs?.map { tab -> tab.id })
        assertEquals("a", next?.selectedTabId)
    }

    @Test
    fun closingOnlyTabClearsSelection() {
        val snapshot = RemoteTabSnapshot(listOf(tab("a", 1)), "a")

        val next = RemoteTabCloser.close(snapshot, "a")

        assertEquals(emptyList<RemoteTab>(), next?.tabs)
        assertNull(next?.selectedTabId)
    }

    @Test
    fun closingUnknownTabIsNoop() {
        val snapshot = RemoteTabSnapshot(listOf(tab("a", 1)), "a")

        val next = RemoteTabCloser.close(snapshot, "missing")

        assertNull(next)
    }

    private fun tab(id: String, index: Int): RemoteTab {
        val uuid = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"
        return RemoteTab(
            id = id,
            request = SessionLinkParser.parse("https://app.warp.dev/session/$uuid", LaunchSource.RESTORE),
            createdAtEpochMillis = index.toLong(),
        )
    }
}
