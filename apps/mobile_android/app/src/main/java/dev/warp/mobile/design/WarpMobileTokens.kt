package dev.warp.mobile.design

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

data class WarpMobileTokens(
    val background: Color,
    val foreground: Color,
    val accent: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val outline: Color,
    val activeText: Color,
    val nonactiveText: Color,
    val disabledText: Color,
    val error: Color,
) {
    companion object {
        fun load(context: Context): WarpMobileTokens {
            val json = context.assets.open("warp-mobile-tokens.default-dark.json")
                .bufferedReader()
                .use { JSONObject(it.readText()) }
            val colors = json.getJSONObject("colors")
            return WarpMobileTokens(
                background = colors.fill("background"),
                foreground = colors.fill("foreground"),
                accent = colors.fill("accent"),
                surface1 = colors.fill("surface1"),
                surface2 = colors.fill("surface2"),
                surface3 = colors.fill("surface3"),
                outline = colors.fill("outline"),
                activeText = colors.color("activeUiText"),
                nonactiveText = colors.color("nonactiveUiText"),
                disabledText = colors.color("disabledUiText"),
                error = colors.color("error"),
            )
        }

        private fun JSONObject.fill(name: String): Color {
            return getJSONObject(name).getString("solidMidpoint").toColor()
        }

        private fun JSONObject.color(name: String): Color {
            return getString(name).toColor()
        }

        private fun String.toColor(): Color {
            val raw = removePrefix("#")
            val r = raw.substring(0, 2).toInt(16)
            val g = raw.substring(2, 4).toInt(16)
            val b = raw.substring(4, 6).toInt(16)
            val a = raw.substring(6, 8).toInt(16)
            return Color(r, g, b, a)
        }
    }
}
