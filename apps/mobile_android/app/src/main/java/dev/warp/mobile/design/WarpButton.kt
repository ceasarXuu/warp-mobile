package dev.warp.mobile.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WarpButtonVariant {
    Primary,
    Secondary,
    Naked,
    Disabled,
}

@Composable
fun WarpButton(
    label: String,
    tokens: WarpMobileTokens,
    variant: WarpButtonVariant,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    onClick: () -> Unit,
) {
    val effectiveVariant = if (enabled) variant else WarpButtonVariant.Disabled
    val colors = buttonColors(tokens, effectiveVariant)
    val shape = RoundedCornerShape(6.dp)
    val clickableModifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    var boxModifier = modifier
        .defaultMinSize(minHeight = 36.dp)
        .clip(shape)
        .then(clickableModifier)

    colors.background?.let { boxModifier = boxModifier.background(it) }
    colors.border?.let { boxModifier = boxModifier.border(BorderStroke(1.dp, it), shape) }

    Box(
        modifier = boxModifier.padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = colors.text, fontSize = 14.sp)
    }
}

private data class ButtonColors(
    val background: Color?,
    val border: Color?,
    val text: Color,
)

private fun buttonColors(tokens: WarpMobileTokens, variant: WarpButtonVariant): ButtonColors {
    return when (variant) {
        WarpButtonVariant.Primary -> ButtonColors(tokens.accent, null, tokens.background)
        WarpButtonVariant.Secondary -> ButtonColors(null, tokens.outline, tokens.foreground)
        WarpButtonVariant.Naked -> ButtonColors(null, null, tokens.foreground)
        WarpButtonVariant.Disabled -> ButtonColors(tokens.surface3, null, tokens.disabledText)
    }
}
