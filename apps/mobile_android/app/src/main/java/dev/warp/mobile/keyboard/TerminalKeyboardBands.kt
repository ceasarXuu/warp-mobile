package dev.warp.mobile.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.warp.mobile.design.WarpMobileTokens

@Composable
internal fun ControlBand(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifierState: TerminalModifierState,
    anchor: KeyboardBandAnchor,
    onAction: (TerminalAction, KeyboardBandAnchor?) -> Unit,
    onPrintable: (String, String, KeyboardBandAnchor?) -> Unit,
) {
    Column(
        modifier = Modifier.padding(end = 6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        KeyRow {
            Key("Esc", "esc", tokens, enabled, onClick = { onAction(TerminalAction.escape(), anchor) })
            Key("Tab", "tab", tokens, enabled, onClick = { onAction(TerminalAction.tab(), anchor) })
        }
        KeyRow {
            Key("Ctrl+C", "ctrl_c", tokens, enabled, emphasis = true, onClick = {
                onAction(TerminalAction.modifiedKey("c", "ctrl_c", listOf(ModifierKey.Ctrl)), anchor)
            })
            Key("Ctrl+D", "ctrl_d", tokens, enabled, onClick = {
                onAction(TerminalAction.modifiedKey("d", "ctrl_d", listOf(ModifierKey.Ctrl)), anchor)
            })
        }
        KeyRow {
            Key("Ctrl+Z", "ctrl_z", tokens, enabled, onClick = {
                onAction(TerminalAction.modifiedKey("z", "ctrl_z", listOf(ModifierKey.Ctrl)), anchor)
            })
            Key("Ctrl+L", "ctrl_l", tokens, enabled, onClick = {
                onAction(TerminalAction.modifiedKey("l", "ctrl_l", listOf(ModifierKey.Ctrl)), anchor)
            })
        }
        listOf(listOf("=", ":", "_"), listOf("\$", "[", "]")).forEach { row ->
            KeyRow {
                row.forEach { symbol ->
                    Key(
                        label = TerminalAction.resolvePrintable(symbol, modifierState),
                        keyId = symbolKeyId(symbol),
                        tokens = tokens,
                        enabled = enabled,
                        onClick = { onPrintable(symbol, symbolKeyId(symbol), anchor) },
                    )
                }
            }
        }
        KeyRow {
            Key(
                label = TerminalAction.resolvePrintable("\\", modifierState),
                keyId = symbolKeyId("\\"),
                tokens = tokens,
                enabled = enabled,
                onClick = { onPrintable("\\", symbolKeyId("\\"), anchor) },
            )
            Key("Bksp", "backspace", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.backspace(), anchor)
            })
            Key("Del", "delete", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.delete(), anchor)
            })
        }
    }
}

@Composable
internal fun KeysBand(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifierState: TerminalModifierState,
    anchor: KeyboardBandAnchor,
    onAction: (TerminalAction, KeyboardBandAnchor?) -> Unit,
    onPrintable: (String, String, KeyboardBandAnchor?) -> Unit,
    onModifier: (ModifierKey) -> Unit,
) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        KeyRow {
            Key("Esc", "esc", tokens, enabled, emphasis = true, onClick = { onAction(TerminalAction.escape(), anchor) })
            listOf("-", "/", ".", "~", "|", "+").forEach { symbol ->
                Key(
                    label = TerminalAction.resolvePrintable(symbol, modifierState),
                    keyId = symbolKeyId(symbol),
                    tokens = tokens,
                    enabled = enabled,
                    onClick = { onPrintable(symbol, symbolKeyId(symbol), anchor) },
                )
            }
            Key("Bksp", "backspace", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.backspace(), anchor)
            })
        }
        KeyRow { "1234567890".forEach { CharKey(it, tokens, enabled, modifierState, anchor, onPrintable) } }
        KeyRow { "qwertyuiop".forEach { CharKey(it, tokens, enabled, modifierState, anchor, onPrintable) } }
        KeyRow { "asdfghjkl".forEach { CharKey(it, tokens, enabled, modifierState, anchor, onPrintable) } }
        KeyRow {
            ModifierKeyButton("Shift", "shift", ModifierKey.Shift, modifierState.shift, tokens, enabled, onModifier)
            "zxcvbnm".forEach { CharKey(it, tokens, enabled, modifierState, anchor, onPrintable) }
        }
        KeyRow {
            ModifierKeyButton("Ctrl", "ctrl", ModifierKey.Ctrl, modifierState.ctrl, tokens, enabled, onModifier)
            ModifierKeyButton("Alt", "alt", ModifierKey.Alt, modifierState.alt, tokens, enabled, onModifier)
            Key("Space", "space", tokens, enabled, weight = 3f, onClick = { onPrintable(" ", "space", anchor) })
            Key("Enter", "enter", tokens, enabled, weight = 2f, primary = true, onClick = {
                onAction(TerminalAction.enter(), anchor)
            })
        }
    }
}

@Composable
internal fun NavigationBand(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifierState: TerminalModifierState,
    anchor: KeyboardBandAnchor,
    onAction: (TerminalAction, KeyboardBandAnchor?) -> Unit,
    onPrintable: (String, String, KeyboardBandAnchor?) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            listOf(";", "&", "*"),
            listOf("<", ">", "?"),
            listOf("!", "(", ")"),
            listOf("[", "]", "\\"),
        ).forEach { row ->
            KeyRow {
                row.forEach { symbol ->
                    Key(
                        label = TerminalAction.resolvePrintable(symbol, modifierState),
                        keyId = symbolKeyId(symbol),
                        tokens = tokens,
                        enabled = enabled,
                        onClick = { onPrintable(symbol, symbolKeyId(symbol), anchor) },
                    )
                }
            }
        }
        KeyRow {
            Spacer(Modifier.weight(1f))
            Key("Up", "arrow_up", tokens, enabled, repeatable = true, emphasis = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowUp), anchor)
            })
            Spacer(Modifier.weight(1f))
        }
        KeyRow {
            Key("Left", "arrow_left", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowLeft), anchor)
            })
            Key("Down", "arrow_down", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowDown), anchor)
            })
            Key("Right", "arrow_right", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowRight), anchor)
            })
        }
    }
}

@Composable
private fun RowScope.CharKey(
    char: Char,
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifierState: TerminalModifierState,
    anchor: KeyboardBandAnchor,
    onPrintable: (String, String, KeyboardBandAnchor?) -> Unit,
) {
    val raw = char.toString()
    Key(
        label = TerminalAction.resolvePrintable(raw, modifierState),
        keyId = raw,
        tokens = tokens,
        enabled = enabled,
        onClick = { onPrintable(raw, raw, anchor) },
    )
}

@Composable
internal fun RowScope.ModifierKeyButton(
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
