package dev.warp.mobile.tabs

object RemoteTabCloser {
    fun close(snapshot: RemoteTabSnapshot, tabId: String): RemoteTabSnapshot? {
        val closingIndex = snapshot.tabs.indexOfFirst { tab -> tab.id == tabId }
        if (closingIndex < 0) return null

        val nextTabs = snapshot.tabs.filterNot { tab -> tab.id == tabId }
        val selectedTabId = when {
            nextTabs.isEmpty() -> null
            snapshot.selectedTabId != tabId && nextTabs.any { tab -> tab.id == snapshot.selectedTabId } -> {
                snapshot.selectedTabId
            }
            else -> nextTabs[minOf(closingIndex, nextTabs.lastIndex)].id
        }

        return RemoteTabSnapshot(nextTabs, selectedTabId)
    }
}
