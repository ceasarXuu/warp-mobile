package dev.warp.mobile.tabs

import android.content.Context
import dev.warp.mobile.observability.MobileEventLogger
import dev.warp.mobile.session.LaunchSource
import dev.warp.mobile.session.SessionLinkParseException
import dev.warp.mobile.session.SessionLinkParser

data class RemoteTabSnapshot(
    val tabs: List<RemoteTab>,
    val selectedTabId: String?,
)

class RemoteTabStore(context: Context) {
    private val preferences = context.getSharedPreferences("remote_tabs", Context.MODE_PRIVATE)

    fun load(logger: MobileEventLogger): RemoteTabSnapshot {
        val rawJson = preferences.getString(KEY_TABS, null)
            ?: return RemoteTabSnapshot(emptyList(), null).also {
                logger.event("mobile_tab_store_loaded", mapOf("tab_count" to "0"))
            }

        val stored = runCatching { RemoteTabPersistence.decode(rawJson) }.getOrElse { error ->
            logger.warn("mobile_tab_store_decode_failed", mapOf("reason" to error.javaClass.simpleName))
            return RemoteTabSnapshot(emptyList(), null)
        }
        val restoredTabs = stored.tabs.mapNotNull { storedTab ->
            try {
                RemoteTab(
                    id = storedTab.id,
                    request = SessionLinkParser.parse(storedTab.rawUrl, LaunchSource.RESTORE),
                    createdAtEpochMillis = storedTab.createdAtEpochMillis,
                )
            } catch (error: SessionLinkParseException) {
                logger.warn(
                    "mobile_tab_restore_dropped",
                    mapOf("tab_id" to storedTab.id, "reason" to error.reason),
                )
                null
            }
        }
        val selectedTabId = stored.selectedTabId?.takeIf { id -> restoredTabs.any { it.id == id } }
            ?: restoredTabs.firstOrNull()?.id
        logger.event("mobile_tab_store_loaded", mapOf("tab_count" to restoredTabs.size.toString()))
        return RemoteTabSnapshot(restoredTabs, selectedTabId)
    }

    fun save(snapshot: RemoteTabSnapshot, logger: MobileEventLogger) {
        val stored = StoredRemoteTabs(
            tabs = snapshot.tabs.map { tab ->
                StoredRemoteTab(
                    id = tab.id,
                    rawUrl = tab.request.loadUrl,
                    createdAtEpochMillis = tab.createdAtEpochMillis,
                )
            },
            selectedTabId = snapshot.selectedTabId,
        )
        preferences.edit().putString(KEY_TABS, RemoteTabPersistence.encode(stored)).apply()
        logger.event("mobile_tab_store_saved", mapOf("tab_count" to snapshot.tabs.size.toString()))
    }

    private companion object {
        const val KEY_TABS = "tabs_snapshot_json"
    }
}
