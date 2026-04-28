package dev.warp.mobile.shell

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.warp.mobile.design.WarpButton
import dev.warp.mobile.design.WarpButtonVariant
import dev.warp.mobile.design.WarpMobileTokens
import dev.warp.mobile.keyboard.TerminalKeyboardBar
import dev.warp.mobile.observability.MobileEventLogger
import dev.warp.mobile.webview.RemoteSessionWebView
import kotlinx.coroutines.delay

@Composable
fun RemoteControlScreen(
    state: RemoteScreenState,
    logger: MobileEventLogger,
    onRetry: () -> Unit,
    onOpenDemo: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = remember { WarpMobileTokens.load(context) }

    when (state) {
        RemoteScreenState.Loading -> LoadingState(tokens)
        is RemoteScreenState.Error -> ErrorState(tokens, state, onRetry, onOpenDemo)
        is RemoteScreenState.Ready -> ReadyState(tokens, state, logger)
    }
}

@Composable
private fun LoadingState(tokens: WarpMobileTokens) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Text("Loading Warp remote session", color = tokens.activeText)
    }
}

@Composable
private fun ErrorState(
    tokens: WarpMobileTokens,
    state: RemoteScreenState.Error,
    onRetry: () -> Unit,
    onOpenDemo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Unable to open remote session", color = tokens.activeText)
        Text("Reason: ${state.reason}", color = tokens.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WarpButton("Retry", tokens, WarpButtonVariant.Secondary, onClick = onRetry)
            WarpButton("Open demo", tokens, WarpButtonVariant.Primary, onClick = onOpenDemo)
        }
    }
}

@Composable
private fun ReadyState(
    tokens: WarpMobileTokens,
    state: RemoteScreenState.Ready,
    logger: MobileEventLogger,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var browserBottom by remember { mutableIntStateOf(-1) }
    var keyboardTop by remember { mutableIntStateOf(-1) }

    LaunchedEffect(browserBottom, keyboardTop, state.request.sessionIdHash) {
        if (browserBottom < 0 || keyboardTop < 0) return@LaunchedEffect
        delay(250)
        logger.event(
            "mobile_shell_keyboard_layout_measured",
            mapOf(
                "browser_bottom_px" to browserBottom.toString(),
                "keyboard_top_px" to keyboardTop.toString(),
                "gap_px" to (keyboardTop - browserBottom).toString(),
                "session_id_hash" to state.request.sessionIdHash,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .safeDrawingPadding(),
    ) {
        Text(
            text = "Remote session ${state.request.sessionIdHash.take(8)}",
            color = tokens.nonactiveText,
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.surface1)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        BrowserPane(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    val next = coordinates.boundsInRoot().bottom.toInt()
                    if (browserBottom != next) {
                        browserBottom = next
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    RemoteSessionWebView.create(context, state.request, logger).also {
                        webView = it
                    }
                },
                update = {
                    webView = it
                    RemoteSessionWebView.update(it, state.request, logger)
                },
            )
        }
        TerminalKeyboardBar(
            tokens = tokens,
            enabled = true,
            sessionIdHash = state.request.sessionIdHash,
            logger = logger,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val next = coordinates.boundsInRoot().top.toInt()
                if (keyboardTop != next) {
                    keyboardTop = next
                }
            },
        ) { action -> RemoteSessionWebView.dispatchTerminalAction(webView, action, logger) }
    }
}

@Composable
private fun BrowserPane(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clipToBounds(),
        content = content,
    )
}
