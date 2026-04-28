package dev.warp.mobile.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.warp.mobile.design.WarpMobileTokens

@Composable
internal fun TerminalSystemKeyboardPanel(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifierState: TerminalModifierState,
    modifier: Modifier = Modifier,
    onSwitchToBuiltinKeyboard: () -> Unit,
    onAction: (TerminalAction) -> Unit,
    onPrintable: (String, String) -> Unit,
    onModifier: (ModifierKey) -> Unit,
) {
    var inputValue by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    DisposableEffect(Unit) {
        onDispose { keyboardController?.hide() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .background(tokens.surface1)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyButton(
                label = "Built-in",
                keyId = "switch_builtin_keyboard",
                tokens = tokens,
                enabled = enabled,
                primary = true,
                modifier = Modifier.weight(1.4f),
                onClick = onSwitchToBuiltinKeyboard,
            )
            ModifierKeyButton("Ctrl", "ctrl", ModifierKey.Ctrl, modifierState.ctrl, tokens, enabled, onModifier)
            ModifierKeyButton("Alt", "alt", ModifierKey.Alt, modifierState.alt, tokens, enabled, onModifier)
            Key("Esc", "esc", tokens, enabled, onClick = { onAction(TerminalAction.escape()) })
            Key("Tab", "tab", tokens, enabled, onClick = { onAction(TerminalAction.tab()) })
            Key("Enter", "enter", tokens, enabled, primary = true, onClick = { onAction(TerminalAction.enter()) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("Left", "arrow_left", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowLeft))
            })
            Key("Down", "arrow_down", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowDown))
            })
            Key("Up", "arrow_up", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowUp))
            })
            Key("Right", "arrow_right", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.navigation(NavigationKey.ArrowRight))
            })
            Key("Bksp", "backspace", tokens, enabled, repeatable = true, onClick = {
                onAction(TerminalAction.backspace())
            })
        }
        BasicTextField(
            value = inputValue,
            onValueChange = { next ->
                if (next.isNotEmpty()) {
                    next.forEach { char ->
                        if (char == '\n') {
                            onAction(TerminalAction.enter())
                        } else {
                            onPrintable(char.toString(), "system_ime_text")
                        }
                    }
                    inputValue = ""
                } else {
                    inputValue = next
                }
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
            cursorBrush = SolidColor(tokens.accent),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = tokens.activeText,
                fontSize = 16.sp,
            ),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(tokens.surface2)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (inputValue.isEmpty()) {
                        Text("System keyboard input", color = tokens.nonactiveText, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}
