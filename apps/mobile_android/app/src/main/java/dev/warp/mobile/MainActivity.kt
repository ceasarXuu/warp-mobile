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

class MainActivity : ComponentActivity() {
    private val logger = MobileEventLogger("WarpMobile")
    private val screenState = mutableStateOf<RemoteScreenState>(RemoteScreenState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.event("mobile_shell_created")
        resolveIntent(intent, LaunchSource.COLD_START)

        setContent {
            RemoteControlScreen(
                state = screenState.value,
                logger = logger,
                onRetry = { resolveIntent(intent, LaunchSource.RETRY) },
                onOpenDemo = { openDemoSession() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logger.event("mobile_shell_new_intent")
        resolveIntent(intent, LaunchSource.NEW_INTENT)
    }

    override fun onDestroy() {
        logger.event("mobile_shell_destroyed")
        super.onDestroy()
    }

    private fun openDemoSession() {
        val demoUrl = "https://debug.warp.local/session/00000000-0000-0000-0000-000000000000"
        resolveUrl(demoUrl, LaunchSource.DEBUG_DEMO)
    }

    private fun resolveIntent(intent: Intent, source: LaunchSource) {
        val rawUrl = intent.dataString
        if (rawUrl == null) {
            openDemoSession()
            return
        }
        resolveUrl(rawUrl, source)
    }

    private fun resolveUrl(rawUrl: String, source: LaunchSource) {
        screenState.value = RemoteScreenState.Loading
        try {
            val request = SessionLinkParser.parse(rawUrl, source)
            logger.event(
                "mobile_link_parse_succeeded",
                mapOf(
                    "source" to source.name,
                    "session_id_hash" to request.sessionIdHash,
                    "redacted_url" to request.redactedUrl,
                ),
            )
            screenState.value = RemoteScreenState.Ready(request)
        } catch (error: SessionLinkParseException) {
            logger.event(
                "mobile_link_parse_failed",
                mapOf("source" to source.name, "reason" to error.reason),
            )
            screenState.value = RemoteScreenState.Error(error.reason, rawUrl)
        }
    }
}
