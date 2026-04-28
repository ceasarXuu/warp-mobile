package dev.warp.mobile.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.warp.mobile.design.WarpMobileTokens
import dev.warp.mobile.observability.MobileEventLogger
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SideBandWidthFactor = 0.42f
private const val AnchorSwitchThresholdFactor = 0.18f

enum class TerminalKeyboardMode {
    Builtin,
    SystemIme,
}

enum class KeyboardBandAnchor(val label: String) {
    LeftPeek("Control"),
    Center("Keys"),
    RightPeek("Nav"),
}

fun KeyboardBandAnchor.resolveAfterDrag(
    startOffsetPx: Float,
    endOffsetPx: Float,
    viewportWidthPx: Float,
    sideBandWidthPx: Float,
): KeyboardBandAnchor {
    val offsetDelta = endOffsetPx - startOffsetPx
    if (abs(offsetDelta) < viewportWidthPx * AnchorSwitchThresholdFactor) return this
    val orderedAnchors = KeyboardBandAnchor.entries
    val currentIndex = orderedAnchors.indexOf(this)
    val candidates = if (offsetDelta > 0f) {
        orderedAnchors.drop(currentIndex + 1)
    } else {
        orderedAnchors.take(currentIndex)
    }
    if (candidates.isEmpty()) return this
    return candidates.minBy { abs(it.offsetForSideBand(sideBandWidthPx) - endOffsetPx) }
}

private fun KeyboardBandAnchor.offsetForSideBand(sideBandWidthPx: Float): Float {
    return when (this) {
        KeyboardBandAnchor.LeftPeek -> 0f
        KeyboardBandAnchor.Center -> sideBandWidthPx
        KeyboardBandAnchor.RightPeek -> sideBandWidthPx * 2f
    }
}

@Composable
fun TerminalKeyboardBar(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    sessionIdHash: String,
    logger: MobileEventLogger,
    onAction: (TerminalAction) -> Unit,
) {
    var mode by remember { mutableStateOf(TerminalKeyboardMode.Builtin) }
    var modifierState by remember { mutableStateOf(TerminalModifierState()) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun setMode(nextMode: TerminalKeyboardMode) {
        if (mode == nextMode) return
        mode = nextMode
        if (nextMode == TerminalKeyboardMode.Builtin) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
        logger.event(
            "mobile_keyboard_mode_changed",
            mapOf("keyboard_mode" to nextMode.name.lowercase(), "session_id_hash" to sessionIdHash),
        )
    }

    fun dispatch(action: TerminalAction, anchor: KeyboardBandAnchor?) {
        if (!enabled) return
        logger.event(
            "mobile_keyboard_action_dispatched",
            action.logFields() + mapOf(
                "keyboard_mode" to mode.name.lowercase(),
                "modifier_state" to modifierState.toLogValue(),
                "session_id_hash" to sessionIdHash,
            ) + anchor?.let { mapOf("anchor" to it.name.lowercase()) }.orEmpty(),
        )
        onAction(action)
        modifierState = modifierState.consumeOneShot()
    }

    fun dispatchPrintable(value: String, keyId: String, anchor: KeyboardBandAnchor?) {
        dispatch(TerminalAction.printable(value, keyId, modifierState), anchor)
    }

    fun toggleModifier(key: ModifierKey) {
        if (!enabled) return
        modifierState = modifierState.toggle(key)
        logger.event(
            "mobile_keyboard_modifier_changed",
            mapOf(
                "keyboard_mode" to mode.name.lowercase(),
                "modifier_key" to key.wireName,
                "modifier_state" to modifierState.toLogValue(),
                "session_id_hash" to sessionIdHash,
            ),
        )
    }

    when (mode) {
        TerminalKeyboardMode.Builtin -> TerminalBuiltinKeyboard(
            tokens = tokens,
            enabled = enabled,
            sessionIdHash = sessionIdHash,
            logger = logger,
            modifierState = modifierState,
            onSwitchToSystemKeyboard = { setMode(TerminalKeyboardMode.SystemIme) },
            onAction = ::dispatch,
            onPrintable = ::dispatchPrintable,
            onModifier = ::toggleModifier,
        )

        TerminalKeyboardMode.SystemIme -> TerminalSystemKeyboardPanel(
            tokens = tokens,
            enabled = enabled,
            modifierState = modifierState,
            onSwitchToBuiltinKeyboard = { setMode(TerminalKeyboardMode.Builtin) },
            onAction = { action -> dispatch(action, null) },
            onPrintable = { value, keyId -> dispatchPrintable(value, keyId, null) },
            onModifier = ::toggleModifier,
        )
    }
}

@Composable
private fun TerminalBuiltinKeyboard(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    sessionIdHash: String,
    logger: MobileEventLogger,
    modifierState: TerminalModifierState,
    onSwitchToSystemKeyboard: () -> Unit,
    onAction: (TerminalAction, KeyboardBandAnchor?) -> Unit,
    onPrintable: (String, String, KeyboardBandAnchor?) -> Unit,
    onModifier: (ModifierKey) -> Unit,
) {
    var anchor by remember { mutableStateOf(KeyboardBandAnchor.Center) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    fun setAnchor(nextAnchor: KeyboardBandAnchor) {
        if (anchor == nextAnchor) return
        anchor = nextAnchor
        logger.event(
            "mobile_keyboard_anchor_changed",
            mapOf("anchor" to nextAnchor.name.lowercase(), "session_id_hash" to sessionIdHash),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(272.dp)
            .background(tokens.surface1)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        BuiltinKeyboardHeader(
            tokens = tokens,
            enabled = enabled,
            anchor = anchor,
            onAnchorSelected = ::setAnchor,
            onSwitchToSystemKeyboard = onSwitchToSystemKeyboard,
        )
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val viewportWidth = maxWidth
            val sideBandWidth = maxWidth * SideBandWidthFactor
            val totalWidth = viewportWidth + sideBandWidth * 2f
            val viewportWidthPx = with(density) { viewportWidth.toPx() }
            val sideBandWidthPx = with(density) { sideBandWidth.toPx() }
            val targetOffsetPx = anchor.offsetForSideBand(sideBandWidthPx)
            val effectiveOffsetPx = (targetOffsetPx - dragOffsetPx)
                .coerceIn(0f, sideBandWidthPx * 2f)

            fun logDragStarted() {
                logger.event(
                    "mobile_keyboard_band_drag_started",
                    mapOf(
                        "anchor" to anchor.name.lowercase(),
                        "start_offset_px" to targetOffsetPx.roundToInt().toString(),
                        "viewport_width_px" to viewportWidthPx.roundToInt().toString(),
                        "session_id_hash" to sessionIdHash,
                    ),
                )
            }

            fun logDragCompleted(endOffsetPx: Float, resolvedAnchor: KeyboardBandAnchor) {
                logger.event(
                    "mobile_keyboard_band_drag_completed",
                    mapOf(
                        "anchor" to anchor.name.lowercase(),
                        "resolved_anchor" to resolvedAnchor.name.lowercase(),
                        "start_offset_px" to targetOffsetPx.roundToInt().toString(),
                        "end_offset_px" to endOffsetPx.roundToInt().toString(),
                        "viewport_width_px" to viewportWidthPx.roundToInt().toString(),
                        "session_id_hash" to sessionIdHash,
                    ),
                )
            }

            fun logDragCanceled(endOffsetPx: Float) {
                logger.event(
                    "mobile_keyboard_band_drag_canceled",
                    mapOf(
                        "anchor" to anchor.name.lowercase(),
                        "start_offset_px" to targetOffsetPx.roundToInt().toString(),
                        "end_offset_px" to endOffsetPx.roundToInt().toString(),
                        "viewport_width_px" to viewportWidthPx.roundToInt().toString(),
                        "session_id_hash" to sessionIdHash,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(anchor, targetOffsetPx, viewportWidthPx, sideBandWidthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragOffsetPx = 0f
                                logDragStarted()
                            },
                            onHorizontalDrag = { _, dragAmount -> dragOffsetPx += dragAmount },
                            onDragEnd = {
                                val endOffsetPx = (targetOffsetPx - dragOffsetPx)
                                    .coerceIn(0f, sideBandWidthPx * 2f)
                                val resolvedAnchor = anchor.resolveAfterDrag(
                                    startOffsetPx = targetOffsetPx,
                                    endOffsetPx = endOffsetPx,
                                    viewportWidthPx = viewportWidthPx,
                                    sideBandWidthPx = sideBandWidthPx,
                                )
                                if (abs(endOffsetPx - targetOffsetPx) < 0.5f) {
                                    logDragCanceled(endOffsetPx)
                                } else {
                                    logDragCompleted(endOffsetPx, resolvedAnchor)
                                }
                                setAnchor(resolvedAnchor)
                                dragOffsetPx = 0f
                            },
                            onDragCancel = {
                                logDragCanceled(
                                    (targetOffsetPx - dragOffsetPx)
                                        .coerceIn(0f, sideBandWidthPx * 2f),
                                )
                                dragOffsetPx = 0f
                            },
                        )
                    },
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentSize(align = Alignment.CenterStart, unbounded = true)
                        .requiredWidth(totalWidth)
                        .offset { IntOffset(-effectiveOffsetPx.roundToInt(), 0) },
                ) {
                    Box(Modifier.width(sideBandWidth)) {
                        ControlBand(tokens, enabled, modifierState, anchor, onAction, onPrintable)
                    }
                    Box(Modifier.width(viewportWidth)) {
                        KeysBand(tokens, enabled, modifierState, anchor, onAction, onPrintable, onModifier)
                    }
                    Box(Modifier.width(sideBandWidth)) {
                        NavigationBand(tokens, enabled, modifierState, anchor, onAction, onPrintable)
                    }
                }
            }
        }
    }
}
