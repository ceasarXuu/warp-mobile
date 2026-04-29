package dev.warp.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import dev.warp.mobile.auth.AuthBrowserLoginReasons
import dev.warp.mobile.auth.WarpLoginBroker
import dev.warp.mobile.observability.MobileEventLogger
import dev.warp.mobile.session.LaunchSource
import dev.warp.mobile.session.SessionLinkParseException
import dev.warp.mobile.session.SessionLinkParser
import dev.warp.mobile.shell.RemoteControlScreen
import dev.warp.mobile.shell.RemoteScreenState
import dev.warp.mobile.tabs.RemoteTabCloser
import dev.warp.mobile.tabs.RemoteTabDeduplicator
import dev.warp.mobile.tabs.RemoteTab
import dev.warp.mobile.tabs.RemoteTabSnapshot
import dev.warp.mobile.tabs.RemoteTabStore
import dev.warp.mobile.webview.RemoteSessionWebView
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val logger = MobileEventLogger("WarpMobile")
    private val screenState = mutableStateOf<RemoteScreenState>(RemoteScreenState.Loading)
    private val isAuthenticated = mutableStateOf(false)
    private val hasRefreshToken = mutableStateOf(false)
    private lateinit var tabStore: RemoteTabStore
    private lateinit var authBroker: WarpLoginBroker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabStore = RemoteTabStore(this)
        authBroker = WarpLoginBroker(this)
        refreshAuthState()
        logger.event("mobile_shell_created")
        resolveLaunch(intent, LaunchSource.COLD_START)

        setContent {
            RemoteControlScreen(
                state = screenState.value,
                logger = logger,
                onRetry = { resolveLaunch(intent, LaunchSource.RETRY) },
                onCreateTab = { rawUrl -> createTab(rawUrl, LaunchSource.USER_CREATED) },
                onSelectTab = { tabId -> selectTab(tabId) },
                onCloseTab = { tabId -> closeTab(tabId) },
                isAuthenticated = isAuthenticated.value,
                canAutoStartSignIn = !hasRefreshToken.value,
                authHandoffProvider = authBroker,
                onSignIn = { reason -> beginBrowserLogin(reason) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logger.event("mobile_shell_new_intent")
        resolveLaunch(intent, LaunchSource.NEW_INTENT)
    }

    override fun onPause() {
        RemoteSessionWebView.flushPersistentState(logger, "activity_pause")
        super.onPause()
    }

    override fun onDestroy() {
        RemoteSessionWebView.flushPersistentState(logger, "activity_destroy")
        logger.event("mobile_shell_destroyed")
        super.onDestroy()
    }

    private fun resolveLaunch(intent: Intent, source: LaunchSource) {
        intent.data?.let { uri ->
            if (authBroker.isAuthRedirect(uri)) {
                handleAuthRedirect(uri)
                return
            }
        }
        val rawUrl = intent.dataString
        if (rawUrl == null) {
            restoreTabs()
            return
        }
        if (isWarpWebContinuation(rawUrl)) {
            routeWebContinuation(rawUrl)
            return
        }
        createTab(rawUrl, source)
    }

    private fun beginBrowserLogin(reason: String = AuthBrowserLoginReasons.User) {
        isAuthenticated.value = false
        authBroker.beginBrowserLogin(this, logger, reason)
    }

    private fun handleAuthRedirect(uri: Uri) {
        val accepted = authBroker.handleAuthRedirect(uri, logger)
        refreshAuthState()
        if (!accepted) {
            restoreTabs()
            return
        }
        reloadSelectedTabAfterAuth()
    }

    private fun refreshAuthState() {
        hasRefreshToken.value = authBroker.hasRefreshToken()
        isAuthenticated.value = authBroker.shouldAttemptEmbeddedSession()
    }

    private fun reloadSelectedTabAfterAuth() {
        val current = screenState.value as? RemoteScreenState.Ready
        val snapshot = current?.let { RemoteTabSnapshot(it.tabs, it.selectedTabId) } ?: tabStore.load(logger)
        val selectedTabId = snapshot.selectedTabId
        val selectedTab = snapshot.tabs.firstOrNull { it.id == selectedTabId }
        if (snapshot.tabs.isEmpty() || selectedTabId == null || selectedTab == null) {
            restoreTabs()
            return
        }
        logger.event("mobile_auth_handoff_reload_requested", mapOf("session_id_hash" to selectedTab.request.sessionIdHash))
        screenState.value = RemoteScreenState.Ready(
            tabs = snapshot.tabs,
            selectedTabId = selectedTabId,
            pendingWebUrl = selectedTab.request.loadUrl,
            webNavigationId = System.currentTimeMillis(),
        )
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

    private fun routeWebContinuation(rawUrl: String) {
        val current = screenState.value as? RemoteScreenState.Ready
        val snapshot = current?.let { RemoteTabSnapshot(it.tabs, it.selectedTabId) } ?: tabStore.load(logger)
        val selectedTabId = snapshot.selectedTabId
        if (snapshot.tabs.isEmpty() || selectedTabId == null) {
            logger.warn("mobile_auth_continuation_rejected", mapOf("reason" to "no_open_tab"))
            screenState.value = RemoteScreenState.Welcome
            return
        }
        logger.event("mobile_auth_continuation_received", mapOf("host" to Uri.parse(rawUrl).host.orEmpty()))
        screenState.value = RemoteScreenState.Ready(
            tabs = snapshot.tabs,
            selectedTabId = selectedTabId,
            pendingWebUrl = rawUrl,
            webNavigationId = System.currentTimeMillis(),
        )
    }

    private fun createTab(rawUrl: String, source: LaunchSource): String? {
        try {
            val request = SessionLinkParser.parse(rawUrl, source)
            val currentTabs = currentTabsForCreate()
            val existingTab = RemoteTabDeduplicator.findExistingTab(currentTabs, request)
            if (existingTab != null) {
                val snapshot = RemoteTabSnapshot(currentTabs, existingTab.id)
                tabStore.save(snapshot, logger)
                logger.event(
                    "mobile_tab_create_deduplicated",
                    mapOf(
                        "source" to source.name,
                        "tab_id" to existingTab.id,
                        "session_id_hash" to request.sessionIdHash,
                    ),
                )
                screenState.value = RemoteScreenState.Ready(currentTabs, existingTab.id)
                return null
            }
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
        screenState.value = ready.copy(selectedTabId = tabId, pendingWebUrl = null)
    }

    private fun closeTab(tabId: String) {
        val ready = screenState.value as? RemoteScreenState.Ready ?: return
        val closingSelectedTab = ready.selectedTabId == tabId
        val nextSnapshot = RemoteTabCloser.close(RemoteTabSnapshot(ready.tabs, ready.selectedTabId), tabId) ?: return
        tabStore.save(nextSnapshot, logger)
        logger.event(
            "mobile_tab_closed",
            mapOf(
                "tab_id" to tabId,
                "closed_selected_tab" to closingSelectedTab.toString(),
                "remaining_count" to nextSnapshot.tabs.size.toString(),
            ),
        )
        val nextSelectedTabId = nextSnapshot.selectedTabId
        if (nextSnapshot.tabs.isEmpty() || nextSelectedTabId == null) {
            logger.event("mobile_tab_welcome_shown")
            screenState.value = RemoteScreenState.Welcome
            return
        }
        screenState.value = RemoteScreenState.Ready(
            tabs = nextSnapshot.tabs,
            selectedTabId = nextSelectedTabId,
            webNavigationId = if (closingSelectedTab) System.currentTimeMillis() else ready.webNavigationId,
        )
    }

    private fun isWarpWebContinuation(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host == "app.warp.dev" &&
            !uri.path.orEmpty().startsWith("/session")
    }
}
