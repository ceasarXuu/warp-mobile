package dev.warp.mobile.shell

import android.webkit.WebView
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import dev.warp.mobile.design.WarpButton
import dev.warp.mobile.design.WarpButtonVariant
import dev.warp.mobile.design.WarpMobileTokens
import dev.warp.mobile.keyboard.TerminalKeyboardBar
import dev.warp.mobile.observability.MobileEventLogger
import dev.warp.mobile.tabs.RemoteTab
import dev.warp.mobile.webview.RemoteSessionWebView
import kotlinx.coroutines.delay

@Composable
fun RemoteControlScreen(
    state: RemoteScreenState,
    logger: MobileEventLogger,
    onRetry: () -> Unit,
    onCreateTab: (String) -> String?,
    onSelectTab: (String) -> Unit,
) {
    val context = LocalContext.current
    val tokens = remember { WarpMobileTokens.load(context) }
    var showCreateDialog by remember { mutableStateOf(false) }

    when (state) {
        RemoteScreenState.Loading -> LoadingState(tokens)
        RemoteScreenState.Welcome -> WelcomeState(tokens) { showCreateDialog = true }
        is RemoteScreenState.Error -> ErrorState(tokens, state, onRetry) { showCreateDialog = true }
        is RemoteScreenState.Ready -> ReadyState(
            tokens = tokens,
            state = state,
            logger = logger,
            onCreateTab = { showCreateDialog = true },
            onSelectTab = onSelectTab,
        )
    }

    if (showCreateDialog) {
        CreateTabDialog(
            tokens = tokens,
            onDismiss = { showCreateDialog = false },
            onCreateTab = { rawUrl ->
                val error = onCreateTab(rawUrl)
                if (error == null) {
                    showCreateDialog = false
                }
                error
            },
        )
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
    onCreateTab: () -> Unit,
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
            WarpButton("New tab", tokens, WarpButtonVariant.Primary, onClick = onCreateTab)
        }
    }
}

@Composable
private fun WelcomeState(
    tokens: WarpMobileTokens,
    onCreateTab: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to Warp mobile", color = tokens.activeText, fontSize = 20.sp)
        Text("Create a remote tab to connect.", color = tokens.nonactiveText, fontSize = 14.sp)
        WarpButton("New tab", tokens, WarpButtonVariant.Primary, onClick = onCreateTab)
    }
}

@Composable
private fun ReadyState(
    tokens: WarpMobileTokens,
    state: RemoteScreenState.Ready,
    logger: MobileEventLogger,
    onCreateTab: () -> Unit,
    onSelectTab: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var browserBottom by remember { mutableIntStateOf(-1) }
    var keyboardTop by remember { mutableIntStateOf(-1) }
    val selectedTab = state.tabs.firstOrNull { it.id == state.selectedTabId } ?: return

    LaunchedEffect(browserBottom, keyboardTop, selectedTab.request.sessionIdHash) {
        if (browserBottom < 0 || keyboardTop < 0) return@LaunchedEffect
        delay(250)
        logger.event(
            "mobile_shell_keyboard_layout_measured",
            mapOf(
                "browser_bottom_px" to browserBottom.toString(),
                "keyboard_top_px" to keyboardTop.toString(),
                "gap_px" to (keyboardTop - browserBottom).toString(),
                "session_id_hash" to selectedTab.request.sessionIdHash,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .safeDrawingPadding(),
    ) {
        RemoteTabStrip(
            tokens = tokens,
            tabs = state.tabs,
            selectedTabId = selectedTab.id,
            onSelectTab = onSelectTab,
            onCreateTab = onCreateTab,
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
                    RemoteSessionWebView.create(context, selectedTab.request, logger).also {
                        webView = it
                    }
                },
                update = {
                    webView = it
                    RemoteSessionWebView.update(it, selectedTab.request, logger)
                },
            )
        }
        TerminalKeyboardBar(
            tokens = tokens,
            enabled = true,
            sessionIdHash = selectedTab.request.sessionIdHash,
            logger = logger,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val next = coordinates.boundsInRoot().top.toInt()
                if (keyboardTop != next) {
                    keyboardTop = next
                }
            },
            onSystemKeyboardOpened = { reason ->
                RemoteSessionWebView.scrollFocusedInputIntoView(webView, logger, reason)
            },
        ) { action -> RemoteSessionWebView.dispatchTerminalAction(webView, action, logger) }
    }
}

@Composable
private fun RemoteTabStrip(
    tokens: WarpMobileTokens,
    tabs: List<RemoteTab>,
    selectedTabId: String,
    onSelectTab: (String) -> Unit,
    onCreateTab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.surface1)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            RemoteTabPill(
                tokens = tokens,
                label = "${index + 1} ${tab.label}",
                selected = tab.id == selectedTabId,
                onClick = { onSelectTab(tab.id) },
            )
        }
        WarpButton(
            "+",
            tokens,
            WarpButtonVariant.Secondary,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            onClick = onCreateTab,
        )
    }
}

@Composable
private fun RemoteTabPill(
    tokens: WarpMobileTokens,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) tokens.surface3 else tokens.surface2)
            .border(1.dp, if (selected) tokens.accent else tokens.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = if (selected) tokens.activeText else tokens.nonactiveText, fontSize = 13.sp)
    }
}

@Composable
private fun CreateTabDialog(
    tokens: WarpMobileTokens,
    onDismiss: () -> Unit,
    onCreateTab: (String) -> String?,
) {
    var rawUrl by remember { mutableStateOf("") }
    var errorReason by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(tokens.surface1)
                .border(1.dp, tokens.outline, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New remote tab", color = tokens.activeText, fontSize = 18.sp)
            UrlInputField(tokens, rawUrl) {
                rawUrl = it
                errorReason = null
            }
            errorReason?.let { Text("Unable to open link: $it", color = tokens.error, fontSize = 13.sp) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarpButton("Cancel", tokens, WarpButtonVariant.Secondary, onClick = onDismiss)
                WarpButton(
                    "Open",
                    tokens,
                    WarpButtonVariant.Primary,
                    enabled = rawUrl.isNotBlank(),
                    onClick = {
                        errorReason = onCreateTab(rawUrl.trim())
                    },
                )
            }
        }
    }
}

@Composable
private fun UrlInputField(
    tokens: WarpMobileTokens,
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = tokens.activeText, fontSize = 14.sp),
        cursorBrush = SolidColor(tokens.accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text("https://app.warp.dev/session/...", color = tokens.disabledText, fontSize = 14.sp)
                }
                innerTextField()
            }
        },
    )
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
