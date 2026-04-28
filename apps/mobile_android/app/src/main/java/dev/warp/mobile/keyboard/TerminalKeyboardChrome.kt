package dev.warp.mobile.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun BuiltinKeyboardHeader(
    tokens: WarpMobileTokens,
    enabled: Boolean,
    anchor: KeyboardBandAnchor,
    onAnchorSelected: (KeyboardBandAnchor) -> Unit,
    onSwitchToSystemKeyboard: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        KeyButton(
            label = "System",
            keyId = "switch_system_ime",
            tokens = tokens,
            enabled = enabled,
            emphasis = true,
            modifier = Modifier.weight(1f),
            onClick = onSwitchToSystemKeyboard,
        )
    }
}

@Composable
internal fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
internal fun RowScope.Key(
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
internal fun KeyButton(
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
    val hapticFeedback = LocalHapticFeedback.current

    fun performClick() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }

    val inputModifier = if (!enabled) {
        Modifier
    } else if (repeatable) {
        Modifier.pointerInput(keyId, enabled) {
            detectTapGestures(
                onPress = {
                    performClick()
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
        Modifier.clickable(onClick = ::performClick)
    }

    Box(
        modifier = modifier
            .height(30.dp)
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

internal fun symbolKeyId(symbol: String): String {
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
