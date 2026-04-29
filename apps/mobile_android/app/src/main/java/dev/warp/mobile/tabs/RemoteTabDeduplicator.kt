package dev.warp.mobile.tabs

import dev.warp.mobile.session.SessionLaunchRequest
import java.net.URI
import java.util.Locale

object RemoteTabDeduplicator {
    fun findExistingTab(tabs: List<RemoteTab>, request: SessionLaunchRequest): RemoteTab? {
        val requestKey = dedupeKey(request)
        return tabs.firstOrNull { tab -> dedupeKey(tab.request) == requestKey }
    }

    fun collapse(snapshot: RemoteTabSnapshot): RemoteTabSnapshot {
        if (snapshot.tabs.size < 2) return snapshot

        val keptByKey = linkedMapOf<String, RemoteTab>()
        val duplicateIdToKeptId = mutableMapOf<String, String>()
        snapshot.tabs.forEach { tab ->
            val key = dedupeKey(tab.request)
            val existing = keptByKey[key]
            if (existing == null) {
                keptByKey[key] = tab
            } else {
                duplicateIdToKeptId[tab.id] = existing.id
            }
        }

        if (duplicateIdToKeptId.isEmpty()) return snapshot

        val keptTabs = keptByKey.values.toList()
        val selectedTabId = when {
            snapshot.selectedTabId == null -> keptTabs.firstOrNull()?.id
            keptTabs.any { tab -> tab.id == snapshot.selectedTabId } -> snapshot.selectedTabId
            else -> duplicateIdToKeptId[snapshot.selectedTabId] ?: keptTabs.firstOrNull()?.id
        }
        return RemoteTabSnapshot(keptTabs, selectedTabId)
    }

    private fun dedupeKey(request: SessionLaunchRequest): String {
        val uri = URI(request.loadUrl)
        val scheme = uri.scheme.orEmpty().lowercase(Locale.US)
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "$scheme://$host$port/session/${request.sessionId.lowercase(Locale.US)}$query"
    }
}
