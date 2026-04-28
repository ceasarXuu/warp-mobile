package dev.warp.mobile.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.warp.mobile.design.WarpMobileTokens
import dev.warp.mobile.observability.MobileEventLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class KeyboardBandAnchor(val label: String) {
    Control("Control"),
    Keys("Keys"),
    Navigation("Nav"),
}

@Composable
fun TerminalKeyboardBar(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    sessionIdHash: String,
    logger: MobileEventLogger,
    onAction: (TerminalAction) -> Unit,
) {
    var anchor by remember { mutableStateOf(KeyboardBandAnchor.Keys) }
    var modifierState by remember { mutableStateOf(TerminalModifierState()) }
    val hapticFeedback = LocalHapticFeedback.current

    fun setAnchor(next: KeyboardBandAnchor) {
        if (anchor == next) return
        anchor = next
        logger.event(
            "mobile_keyboard_anchor_changed",
            mapOf("anchor" to next.name.lowercase(), "session_id_hash" to sessionIdHash),
        )
    }

    fun dispatch(action: TerminalAction) {
        if (!enabled) return
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        logger.event(
            "mobile_keyboard_action_dispatched",
            action.logFields() + mapOf(
                "modifier_state" to modifierState.toLogValue(),
                "anchor" to anchor.name.lowercase(),
                "session_id_hash" to sessionIdHash,
            ),
        )
        onAction(action)
        modifierState = modifierState.consumeOneShot()
    }

    fun dispatchPrintable(value: String, keyId: String) {
        dispatch(TerminalAction.printable(value, keyId, modifierState))
    }

    fun toggleModifier(key: ModifierKey) {
        if (!enabled) return
        modifierState = modifierState.toggle(key)
        logger.event(
            "mobile_keyboard_modifier_changed",
            mapOf(
                "modifier_key" to key.wireName,
                "modifier_state" to modifierState.toLogValue(),
                "session_id_hash" to sessionIdHash,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(272.dp)
            .background(tokens.surface1)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyboardHeader(tokens, anchor, enabled, ::setAnchor)
        when (anchor) {
            KeyboardBandAnchor.Control -> ControlBand(tokens, enabled, modifierState, ::dispatch, ::dispatchPrintable)
            KeyboardBandAnchor.Keys -> KeysBand(tokens, enabled, modifierState, ::dispatch, ::dispatchPrintable, ::toggleModifier)
            KeyboardBandAnchor.Navigation -> NavigationBand(tokens, enabled, ::dispatch)
        }
    }
}

@Composable
private fun KeyboardHeader(
    tokens: WarpMobileTokens,
    anchor: KeyboardBandAnchor,
    enabled: Boolean,
    onAnchorSelected: (KeyboardBandAnchor) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyboardBandAnchor.entries.forEach { item ->
            KeyButton(
                label = item.label,
                keyId = "anchor_${item.name.lowercase()}",
                tokens = tokens,
                enabled = enabled,
                active = item == anchor,
                modifier = Modifier.weight(1f),
                onClick = { onAnchorSelected(item) },
            )
        }
    }
}

@Composable
private fun ControlBand(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifierState: TerminalModifierState,
    onAction: (TerminalAction) -> Unit,
    onPrintable: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyRow {
            Key("Esc", "esc", tokens, enabled, onClick = { onAction(TerminalAction.escape()) })
            Key("Tab", "tab", tokens, enabled, onClick = { onAction(TerminalAction.tab()) })
            Key("Ctrl+C", "ctrl_c", tokens, enabled, emphasis = true, onClick = {
                onAction(TerminalAction.modifiedKey("c", "ctrl_c", listOf(ModifierKey.Ctrl)))
            })
            Key("Ctrl+D", "ctrl_d", tokens, enabled, onClick = {
                onAction(TerminalAction.modifiedKey("d", "ctrl_d", listOf(ModifierKey.Ctrl)))
            })
        }
        KeyRow {
            Key("Ctrl+Z", "ctrl_z", tokens, enabled, onClick = {
                onAction(TerminalAction.modifiedKey("z", "ctrl_z", listOf(ModifierKey.Ctrl)))
            })
            Key("Ctrl+L", "ctrl_l", tokens, enabled, onClick = {
                onAction(TerminalAction.modifiedKey("l", "ctrl_l", listOf(ModifierKey.Ctrl)))
            })
            Key("Bksp", "backspace", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.backspace())
            })
            Key("Del", "delete", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.delete())
            })
        }
        KeyRow {
            listOf("=", ":", "_", "\$", "[", "]", "\\").forEach { symbol ->
                Key(
                    label = TerminalAction.resolvePrintable(symbol, modifierState),
                    keyId = symbolKeyId(symbol),
                    tokens = tokens,
                    enabled = enabled,
                    onClick = { onPrintable(symbol, symbolKeyId(symbol)) },
                )
            }
        }
    }
}

@Composable
private fun KeysBand(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifierState: TerminalModifierState,
    onAction: (TerminalAction) -> Unit,
    onPrintable: (String, String) -> Unit,
    onModifier: (ModifierKey) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyRow {
            Key("Esc", "esc", tokens, enabled, emphasis = true, onClick = { onAction(TerminalAction.escape()) })
            listOf("-", "/", ".", "~", "|", "+").forEach { symbol ->
                Key(
                    label = TerminalAction.resolvePrintable(symbol, modifierState),
                    keyId = symbolKeyId(symbol),
                    tokens = tokens,
                    enabled = enabled,
                    onClick = { onPrintable(symbol, symbolKeyId(symbol)) },
                )
            }
            Key("Bksp", "backspace", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.backspace())
            })
        }
        KeyRow { "1234567890".forEach { CharKey(it, tokens, enabled, modifierState, onPrintable) } }
        KeyRow { "qwertyuiop".forEach { CharKey(it, tokens, enabled, modifierState, onPrintable) } }
        KeyRow { "asdfghjkl".forEach { CharKey(it, tokens, enabled, modifierState, onPrintable) } }
        KeyRow {
            ModifierKeyButton("Shift", "shift", ModifierKey.Shift, modifierState.shift, tokens, enabled, onModifier)
            "zxcvbnm".forEach { CharKey(it, tokens, enabled, modifierState, onPrintable) }
        }
        KeyRow {
            ModifierKeyButton("Ctrl", "ctrl", ModifierKey.Ctrl, modifierState.ctrl, tokens, enabled, onModifier)
            ModifierKeyButton("Alt", "alt", ModifierKey.Alt, modifierState.alt, tokens, enabled, onModifier)
            Key("Space", "space", tokens, enabled, weight = 3f, onClick = { onPrintable(" ", "space") })
            Key("Enter", "enter", tokens, enabled, weight = 2f, primary = true, onClick = {
                onAction(TerminalAction.enter())
            })
        }
    }
}

@Composable
private fun NavigationBand(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    onAction: (TerminalAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyRow {
            Key("PgUp", "page_up", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.PageUp))
            })
            Key("Home", "home", tokens, enabled, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.Home))
            })
            Key("Up", "arrow_up", tokens, enabled, repeatable = true, emphasis = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowUp))
            })
            Key("End", "end", tokens, enabled, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.End))
            })
            Key("PgDn", "page_down", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.PageDown))
            })
        }
        KeyRow {
            Spacer(Modifier.weight(1f))
            Key("Left", "arrow_left", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowLeft))
            })
            Key("Down", "arrow_down", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowDown))
            })
            Key("Right", "arrow_right", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowRight))
            })
            Spacer(Modifier.weight(1f))
        }
        KeyRow {
            listOf(";", "&", "*", "<", ">", "?", "!", "(", ")").forEach { symbol ->
                Key(symbol, symbolKeyId(symbol), tokens, enabled, onClick = {
                    onAction(TerminalAction.raw(symbolKeyId(symbol), symbol))
                })
            }
        }
        KeyRow {
            Key("Del", "delete", tokens, enabled, repeatable = true, emphasis = true, onClick = {
                onAction(TerminalAction.delete())
            })
            Key("Bksp", "backspace", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.backspace())
            })
            Key("Enter", "enter", tokens, enabled, primary = true, onClick = {
                onAction(TerminalAction.enter())
            })
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun RowScope.CharKey(
    char: Char,
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifierState: TerminalModifierState,
    onPrintable: (String, String) -> Unit,
) {
    val raw = char.toString()
    Key(
        label = TerminalAction.resolvePrintable(raw, modifierState),
        keyId = raw,
        tokens = tokens,
        enabled = enabled,
        onClick = { onPrintable(raw, raw) },
    )
}

@Composable
private fun RowScope.ModifierKeyButton(
    label: String,
    keyId: String,
    key: ModifierKey,
    latch: ModifierLatch,
    tokens: WarpMobileTokens,
    enabled: Boolean,
    onModifier: (ModifierKey) -> Unit,
) {
    val active = latch != ModifierLatch.Inactive
    Key(
        label = if (latch == ModifierLatch.Locked) "$label L" else label,
        keyId = keyId,
        tokens = tokens,
        enabled = enabled,
        active = active,
        onClick = { onModifier(key) },
    )
}

@Composable
private fun RowScope.Key(
    label: String,
    keyId: String,
    tokens: WarpMobileTokens,
    enabled: Boolean,
    weight: Float = 1f,
    active: Boolean = false,
    emphasis: Boolean = false,
    primary: Boolean = false,
    repeatable: Boolean = false,
    onClick: () -> Unit,
) {
    KeyButton(
        label = label,
        keyId = keyId,
        tokens = tokens,
        enabled = enabled,
        active = active,
        emphasis = emphasis,
        primary = primary,
        repeatable = repeatable,
        modifier = Modifier.weight(weight),
        onClick = onClick,
    )
}

@Composable
private fun KeyButton(
    label: String,
    keyId: String,
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    emphasis: Boolean = false,
    primary: Boolean = false,
    repeatable: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val colors = keyColors(tokens, enabled, active, emphasis, primary)
    val inputModifier = if (!enabled) {
        Modifier
    } else if (repeatable) {
        Modifier.pointerInput(keyId, enabled) {
            detectTapGestures(
                onPress = {
                    onClick()
                    coroutineScope {
                        val repeatJob = launch {
                            delay(500)
                            while (isActive) {
                                onClick()
                                delay(80)
                            }
                        }
                        tryAwaitRelease()
                        repeatJob.cancel()
                    }
                },
            )
        }
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.border, shape)
            .then(inputModifier)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = if (active || primary || emphasis) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private data class KeyColors(
    val background: Color,
    val border: Color,
    val text: Color,
)

private fun keyColors(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    active: Boolean,
    emphasis: Boolean,
    primary: Boolean,
): KeyColors {
    if (!enabled) return KeyColors(tokens.surface3, tokens.surface3, tokens.disabledText)
    if (primary || active) return KeyColors(tokens.accent, tokens.accent, tokens.background)
    if (emphasis) return KeyColors(tokens.surface3, tokens.outline, tokens.activeText)
    return KeyColors(tokens.surface2, tokens.outline, tokens.foreground)
}

private fun symbolKeyId(symbol: String): String {
    return when (symbol) {
        "-" -> "minus"
        "/" -> "slash"
        "." -> "period"
        "_" -> "underscore"
        "~" -> "tilde"
        "|" -> "pipe"
        "\$" -> "dollar"
        ":" -> "colon"
        ";" -> "semicolon"
        "&" -> "ampersand"
        "*" -> "asterisk"
        "+" -> "plus"
        "<" -> "less_than"
        ">" -> "greater_than"
        "?" -> "question_mark"
        "!" -> "bang"
        "(" -> "left_paren"
        ")" -> "right_paren"
        "=" -> "equals"
        "[" -> "left_bracket"
        "]" -> "right_bracket"
        "\\" -> "backslash"
        else -> symbol
    }
}
