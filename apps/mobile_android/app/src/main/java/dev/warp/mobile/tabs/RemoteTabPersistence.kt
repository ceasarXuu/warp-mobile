package dev.warp.mobile.tabs

import org.json.JSONArray
import org.json.JSONObject

data class StoredRemoteTab(
    val id: String,
    val rawUrl: String,
    val createdAtEpochMillis: Long,
)

data class StoredRemoteTabs(
    val tabs: List<StoredRemoteTab>,
    val selectedTabId: String?,
)

object RemoteTabPersistence {
    fun encode(snapshot: StoredRemoteTabs): String {
        val root = JSONObject()
        root.put("schema", 1)
        root.put("selectedTabId", snapshot.selectedTabId)
        root.put(
            "tabs",
            JSONArray().apply {
                snapshot.tabs.forEach { tab ->
                    put(
                        JSONObject()
                            .put("id", tab.id)
                            .put("rawUrl", tab.rawUrl)
                            .put("createdAtEpochMillis", tab.createdAtEpochMillis),
                    )
                }
            },
        )
        return root.toString()
    }

    fun decode(rawJson: String): StoredRemoteTabs {
        val root = JSONObject(rawJson)
        val tabsJson = root.optJSONArray("tabs") ?: JSONArray()
        val tabs = buildList {
            for (index in 0 until tabsJson.length()) {
                val tabJson = tabsJson.optJSONObject(index) ?: continue
                val id = tabJson.optString("id").takeIf { it.isNotBlank() } ?: continue
                val rawUrl = tabJson.optString("rawUrl").takeIf { it.isNotBlank() } ?: continue
                add(
                    StoredRemoteTab(
                        id = id,
                        rawUrl = rawUrl,
                        createdAtEpochMillis = tabJson.optLong("createdAtEpochMillis", 0L),
                    ),
                )
            }
        }
        val selectedTabId = root.optString("selectedTabId").takeIf { it.isNotBlank() }
        return StoredRemoteTabs(tabs = tabs, selectedTabId = selectedTabId)
    }
}
