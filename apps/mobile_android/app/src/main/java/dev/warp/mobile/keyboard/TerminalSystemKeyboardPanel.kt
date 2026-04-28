package dev.warp.mobile.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.warp.mobile.design.WarpMobileTokens

@Composable
internal fun TerminalSystemKeyboardPanel(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSwitchToBuiltinKeyboard: () -> Unit,
    onAction: (TerminalAction) -> Unit,
    onPrintable: (String, String) -> Unit,
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
    ) {
        KeyButton(
            label = "Built-in keyboard",
            keyId = "switch_builtin_keyboard",
            tokens = tokens,
            enabled = enabled,
            primary = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSwitchToBuiltinKeyboard,
        )
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
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .focusRequester(focusRequester),
        )
    }
}
