package dev.warp.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import dev.warp.mobile.observability.MobileEventLogger
import dev.warp.mobile.session.LaunchSource
import dev.warp.mobile.session.SessionLinkParseException
import dev.warp.mobile.session.SessionLinkParser
import dev.warp.mobile.shell.RemoteControlScreen
import dev.warp.mobile.shell.RemoteScreenState
import dev.warp.mobile.tabs.RemoteTab
import dev.warp.mobile.tabs.RemoteTabSnapshot
import dev.warp.mobile.tabs.RemoteTabStore
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val logger = MobileEventLogger("WarpMobile")
    private val screenState = mutableStateOf<RemoteScreenState>(RemoteScreenState.Loading)
    private lateinit var tabStore: RemoteTabStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabStore = RemoteTabStore(this)
        logger.event("mobile_shell_created")
        resolveLaunch(intent, LaunchSource.COLD_START)

        setContent {
            RemoteControlScreen(
                state = screenState.value,
                logger = logger,
                onRetry = { resolveLaunch(intent, LaunchSource.RETRY) },
                onCreateTab = { rawUrl -> createTab(rawUrl, LaunchSource.USER_CREATED) },
                onSelectTab = { tabId -> selectTab(tabId) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logger.event("mobile_shell_new_intent")
        resolveLaunch(intent, LaunchSource.NEW_INTENT)
    }

    override fun onDestroy() {
        logger.event("mobile_shell_destroyed")
        super.onDestroy()
    }

    private fun resolveLaunch(intent: Intent, source: LaunchSource) {
        val rawUrl = intent.dataString
        if (rawUrl == null) {
            restoreTabs()
            return
        }
        createTab(rawUrl, source)
    }

    private fun restoreTabs() {
        screenState.value = RemoteScreenState.Loading
        val snapshot = tabStore.load(logger)
        if (snapshot.tabs.isEmpty() || snapshot.selectedTabId == null) {
            logger.event("mobile_tab_welcome_shown")
            screenState.value = RemoteScreenState.Welcome
            return
        }
        screenState.value = RemoteScreenState.Ready(snapshot.tabs, snapshot.selectedTabId)
    }

    private fun createTab(rawUrl: String, source: LaunchSource): String? {
        try {
            val request = SessionLinkParser.parse(rawUrl, source)
            val currentTabs = currentTabsForCreate()
            val tab = RemoteTab(
                id = UUID.randomUUID().toString(),
                request = request,
                createdAtEpochMillis = System.currentTimeMillis(),
            )
            val nextTabs = currentTabs + tab
            val snapshot = RemoteTabSnapshot(nextTabs, tab.id)
            tabStore.save(snapshot, logger)
            logger.event(
                "mobile_link_parse_succeeded",
                mapOf(
                    "source" to source.name,
                    "session_id_hash" to request.sessionIdHash,
                    "redacted_url" to request.redactedUrl,
                ),
            )
            logger.event(
                "mobile_tab_created",
                mapOf("tab_id" to tab.id, "session_id_hash" to request.sessionIdHash),
            )
            screenState.value = RemoteScreenState.Ready(nextTabs, tab.id)
            return null
        } catch (error: SessionLinkParseException) {
            logger.event(
                "mobile_link_parse_failed",
                mapOf("source" to source.name, "reason" to error.reason),
            )
            if (source != LaunchSource.USER_CREATED) {
                screenState.value = RemoteScreenState.Error(error.reason, rawUrl)
            }
            logger.warn("mobile_tab_create_failed", mapOf("source" to source.name, "reason" to error.reason))
            return error.reason
        }
    }

    private fun currentTabsForCreate(): List<RemoteTab> {
        return (screenState.value as? RemoteScreenState.Ready)?.tabs
            ?: tabStore.load(logger).tabs
    }

    private fun selectTab(tabId: String) {
        val ready = screenState.value as? RemoteScreenState.Ready ?: return
        if (ready.tabs.none { it.id == tabId }) return
        if (ready.selectedTabId == tabId) return
        val snapshot = RemoteTabSnapshot(ready.tabs, tabId)
        tabStore.save(snapshot, logger)
        logger.event("mobile_tab_selected", mapOf("tab_id" to tabId))
        screenState.value = ready.copy(selectedTabId = tabId)
    }
}
