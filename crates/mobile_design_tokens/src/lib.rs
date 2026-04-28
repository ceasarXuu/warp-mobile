use serde::{Deserialize, Serialize};
use serde_json::Value;
use warp_core::ui::theme::{
    color::internal_colors, AnsiColor, AnsiColors, Fill, TerminalColors, WarpTheme,
};
use warpui::color::ColorU;

const SCHEMA_VERSION: u32 = 1;

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileDesignTokenDocument {
    pub schema_version: u32,
    pub source: MobileTokenSource,
    pub colors: MobileColorTokens,
    pub terminal_colors: MobileTerminalColors,
    pub components: MobileComponentTokens,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileTokenSource {
    pub theme_name: String,
    pub generated_from: String,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileColorTokens {
    pub background: MobileFillToken,
    pub foreground: MobileFillToken,
    pub accent: MobileFillToken,
    pub surface_1: MobileFillToken,
    pub surface_2: MobileFillToken,
    pub surface_3: MobileFillToken,
    pub outline: MobileFillToken,
    pub text_main: String,
    pub text_sub: String,
    pub text_hint: String,
    pub text_disabled: String,
    pub active_ui_text: String,
    pub nonactive_ui_text: String,
    pub disabled_ui_text: String,
    pub error: String,
    pub warning: String,
    pub green: String,
    pub yellow: String,
    pub surface_overlay_1: MobileFillToken,
    pub surface_overlay_2: MobileFillToken,
    pub surface_overlay_3: MobileFillToken,
    pub accent_overlay: MobileFillToken,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileFillToken {
    pub kind: FillKind,
    pub raw_warp_fill: Value,
    pub solid_midpoint: String,
    pub solid_bias_top: String,
    pub solid_bias_right: String,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum FillKind {
    Solid,
    VerticalGradient,
    HorizontalGradient,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileTerminalColors {
    pub normal: MobileAnsiColors,
    pub bright: MobileAnsiColors,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileAnsiColors {
    pub black: String,
    pub red: String,
    pub green: String,
    pub yellow: String,
    pub blue: String,
    pub magenta: String,
    pub cyan: String,
    pub white: String,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileComponentTokens {
    pub buttons: MobileButtonTokens,
    pub keyboard: MobileKeyboardTokens,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileButtonTokens {
    pub primary: MobileButtonVariantToken,
    pub secondary: MobileButtonVariantToken,
    pub naked: MobileButtonVariantToken,
    pub disabled: MobileButtonVariantToken,
    pub danger_primary: MobileButtonVariantToken,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileButtonVariantToken {
    pub warp_variant: String,
    pub default_background: Option<MobileFillToken>,
    pub hover_background: Option<MobileFillToken>,
    pub pressed_background: Option<MobileFillToken>,
    pub border: Option<String>,
    pub text: String,
    pub keyboard_shortcut_background: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MobileKeyboardTokens {
    pub printable_key_variant: String,
    pub tool_key_variant: String,
    pub primary_key_variant: String,
    pub disabled_key_variant: String,
    pub active_modifier_border_token: String,
    pub active_modifier_background_token: String,
    pub more_sheet_component: String,
}

pub fn mobile_design_tokens_from_theme(
    theme_name: impl Into<String>,
    theme: &WarpTheme,
) -> MobileDesignTokenDocument {
    MobileDesignTokenDocument {
        schema_version: SCHEMA_VERSION,
        source: MobileTokenSource {
            theme_name: theme_name.into(),
            generated_from: "warp_core::ui::theme::WarpTheme".to_owned(),
        },
        colors: color_tokens(theme),
        terminal_colors: terminal_colors(theme.terminal_colors()),
        components: component_tokens(theme),
    }
}

pub fn to_pretty_json(document: &MobileDesignTokenDocument) -> serde_json::Result<String> {
    serde_json::to_string_pretty(document)
}

fn color_tokens(theme: &WarpTheme) -> MobileColorTokens {
    let background = theme.background();
    MobileColorTokens {
        background: fill_token(background),
        foreground: fill_token(theme.foreground()),
        accent: fill_token(theme.accent()),
        surface_1: fill_token(theme.surface_1()),
        surface_2: fill_token(theme.surface_2()),
        surface_3: fill_token(theme.surface_3()),
        outline: fill_token(theme.outline()),
        text_main: color_hex(theme.main_text_color(background).into_solid()),
        text_sub: color_hex(theme.sub_text_color(background).into_solid()),
        text_hint: color_hex(theme.hint_text_color(background).into_solid()),
        text_disabled: color_hex(theme.disabled_text_color(background).into_solid()),
        active_ui_text: color_hex(theme.active_ui_text_color().into_solid()),
        nonactive_ui_text: color_hex(theme.nonactive_ui_text_color().into_solid()),
        disabled_ui_text: color_hex(theme.disabled_ui_text_color().into_solid()),
        error: color_hex(theme.ui_error_color()),
        warning: color_hex(theme.ui_warning_color()),
        green: color_hex(theme.ui_green_color()),
        yellow: color_hex(theme.ui_yellow_color()),
        surface_overlay_1: fill_token(theme.surface_overlay_1()),
        surface_overlay_2: fill_token(theme.surface_overlay_2()),
        surface_overlay_3: fill_token(theme.surface_overlay_3()),
        accent_overlay: fill_token(theme.accent_overlay()),
    }
}

fn component_tokens(theme: &WarpTheme) -> MobileComponentTokens {
    MobileComponentTokens {
        buttons: MobileButtonTokens {
            primary: primary_button(theme),
            secondary: secondary_button(theme),
            naked: naked_button(theme),
            disabled: disabled_button(theme),
            danger_primary: danger_primary_button(theme),
        },
        keyboard: MobileKeyboardTokens {
            printable_key_variant: "Secondary".to_owned(),
            tool_key_variant: "Naked".to_owned(),
            primary_key_variant: "Primary".to_owned(),
            disabled_key_variant: "Disabled".to_owned(),
            active_modifier_border_token: "colors.accent".to_owned(),
            active_modifier_background_token: "colors.accentOverlay".to_owned(),
            more_sheet_component: "Dialog".to_owned(),
        },
    }
}

fn primary_button(theme: &WarpTheme) -> MobileButtonVariantToken {
    let background = theme.accent();
    MobileButtonVariantToken {
        warp_variant: "Primary".to_owned(),
        default_background: Some(fill_token(background)),
        hover_background: Some(fill_token(internal_colors::accent_overlay_4(theme))),
        pressed_background: Some(fill_token(internal_colors::accent_overlay_3(theme))),
        border: None,
        text: color_hex(theme.font_color(background).into_solid()),
        keyboard_shortcut_background: None,
    }
}

fn secondary_button(theme: &WarpTheme) -> MobileButtonVariantToken {
    MobileButtonVariantToken {
        warp_variant: "Secondary".to_owned(),
        default_background: None,
        hover_background: Some(fill_token(internal_colors::fg_overlay_2(theme))),
        pressed_background: Some(fill_token(internal_colors::fg_overlay_3(theme))),
        border: Some(color_hex(internal_colors::neutral_4(theme))),
        text: color_hex(theme.foreground().into_solid()),
        keyboard_shortcut_background: Some(color_hex(internal_colors::neutral_3(theme))),
    }
}

fn naked_button(theme: &WarpTheme) -> MobileButtonVariantToken {
    MobileButtonVariantToken {
        warp_variant: "Naked".to_owned(),
        default_background: None,
        hover_background: Some(fill_token(internal_colors::fg_overlay_2(theme))),
        pressed_background: Some(fill_token(internal_colors::fg_overlay_3(theme))),
        border: None,
        text: color_hex(theme.foreground().into_solid()),
        keyboard_shortcut_background: Some(color_hex(internal_colors::neutral_3(theme))),
    }
}

fn disabled_button(theme: &WarpTheme) -> MobileButtonVariantToken {
    MobileButtonVariantToken {
        warp_variant: "Disabled".to_owned(),
        default_background: Some(fill_token(Fill::Solid(internal_colors::neutral_4(theme)))),
        hover_background: Some(fill_token(Fill::Solid(internal_colors::neutral_4(theme)))),
        pressed_background: Some(fill_token(Fill::Solid(internal_colors::neutral_4(theme)))),
        border: None,
        text: color_hex(internal_colors::neutral_5(theme)),
        keyboard_shortcut_background: Some(color_hex(internal_colors::neutral_3(theme))),
    }
}

fn danger_primary_button(theme: &WarpTheme) -> MobileButtonVariantToken {
    let background = Fill::Solid(theme.ansi_fg_red());
    MobileButtonVariantToken {
        warp_variant: "DangerPrimary".to_owned(),
        default_background: Some(fill_token(background)),
        hover_background: Some(fill_token(Fill::Solid(ColorU::new(255, 130, 114, 255)))),
        pressed_background: Some(fill_token(background)),
        border: None,
        text: color_hex(theme.font_color(background).into_solid()),
        keyboard_shortcut_background: None,
    }
}

fn fill_token(fill: Fill) -> MobileFillToken {
    MobileFillToken {
        kind: fill_kind(fill),
        raw_warp_fill: serde_json::to_value(fill).expect("Fill serialization should not fail"),
        solid_midpoint: color_hex(fill.into_solid()),
        solid_bias_top: color_hex(fill.into_solid_bias_top_color()),
        solid_bias_right: color_hex(fill.into_solid_bias_right_color()),
    }
}

fn fill_kind(fill: Fill) -> FillKind {
    match fill {
        Fill::Solid(_) => FillKind::Solid,
        Fill::VerticalGradient(_) => FillKind::VerticalGradient,
        Fill::HorizontalGradient(_) => FillKind::HorizontalGradient,
    }
}

fn terminal_colors(colors: &TerminalColors) -> MobileTerminalColors {
    MobileTerminalColors {
        normal: ansi_colors(colors.normal),
        bright: ansi_colors(colors.bright),
    }
}

fn ansi_colors(colors: AnsiColors) -> MobileAnsiColors {
    MobileAnsiColors {
        black: ansi_hex(colors.black),
        red: ansi_hex(colors.red),
        green: ansi_hex(colors.green),
        yellow: ansi_hex(colors.yellow),
        blue: ansi_hex(colors.blue),
        magenta: ansi_hex(colors.magenta),
        cyan: ansi_hex(colors.cyan),
        white: ansi_hex(colors.white),
    }
}

fn ansi_hex(color: AnsiColor) -> String {
    color_hex(color.into())
}

fn color_hex(color: ColorU) -> String {
    format!(
        "#{:02x}{:02x}{:02x}{:02x}",
        color.r, color.g, color.b, color.a
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use warp_core::ui::theme::{
        AnsiColor, AnsiColors, Details, HorizontalGradient, TerminalColors, WarpTheme,
    };

    fn test_terminal_colors() -> TerminalColors {
        TerminalColors::new(
            AnsiColors::new(
                AnsiColor::from_u32(0x000000ff),
                AnsiColor::from_u32(0xff0000ff),
                AnsiColor::from_u32(0x00ff00ff),
                AnsiColor::from_u32(0xffff00ff),
                AnsiColor::from_u32(0x0000ffff),
                AnsiColor::from_u32(0xff00ffff),
                AnsiColor::from_u32(0x00ffffff),
                AnsiColor::from_u32(0xffffffff),
            ),
            AnsiColors::new(
                AnsiColor::from_u32(0x111111ff),
                AnsiColor::from_u32(0xff1111ff),
                AnsiColor::from_u32(0x11ff11ff),
                AnsiColor::from_u32(0xffff11ff),
                AnsiColor::from_u32(0x1111ffff),
                AnsiColor::from_u32(0xff11ffff),
                AnsiColor::from_u32(0x11ffffff),
                AnsiColor::from_u32(0xffffffff),
            ),
        )
    }

    fn test_theme() -> WarpTheme {
        WarpTheme::new(
            Fill::Solid(ColorU::from_u32(0x000000ff)),
            ColorU::from_u32(0xffffffff),
            Fill::Solid(ColorU::from_u32(0x19aad8ff)),
            None,
            Some(Details::Darker),
            test_terminal_colors(),
            None,
            Some("Test Dark".to_owned()),
        )
    }

    #[test]
    fn exports_core_warp_theme_tokens() {
        let document = mobile_design_tokens_from_theme("test-dark", &test_theme());

        assert_eq!(document.schema_version, 1);
        assert_eq!(document.source.theme_name, "test-dark");
        assert_eq!(document.colors.background.solid_midpoint, "#000000ff");
        assert_eq!(document.colors.foreground.solid_midpoint, "#ffffffff");
        assert_eq!(document.colors.accent.solid_midpoint, "#19aad8ff");
        assert_eq!(document.components.buttons.primary.warp_variant, "Primary");
        assert_eq!(
            document.components.buttons.secondary.warp_variant,
            "Secondary"
        );
        assert_eq!(
            document.components.keyboard.printable_key_variant,
            "Secondary"
        );
    }

    #[test]
    fn preserves_gradient_kind_and_mobile_fallbacks() {
        let theme = WarpTheme::new(
            Fill::Solid(ColorU::from_u32(0x000000ff)),
            ColorU::from_u32(0xffffffff),
            Fill::HorizontalGradient(HorizontalGradient::new(
                ColorU::from_u32(0x000000ff),
                ColorU::from_u32(0xffffffff),
            )),
            None,
            Some(Details::Darker),
            test_terminal_colors(),
            None,
            Some("Gradient".to_owned()),
        );

        let document = mobile_design_tokens_from_theme("gradient", &theme);

        assert_eq!(document.colors.accent.kind, FillKind::HorizontalGradient);
        assert_eq!(document.colors.accent.solid_bias_right, "#ffffffff");
        assert_ne!(
            document.colors.accent.solid_midpoint,
            document.colors.accent.solid_bias_right
        );
    }

    #[test]
    fn serializes_to_pretty_json() {
        let document = mobile_design_tokens_from_theme("test-dark", &test_theme());
        let json = to_pretty_json(&document).expect("document should serialize");

        assert!(json.contains("\"schemaVersion\": 1"));
        assert!(json.contains("\"themeName\": \"test-dark\""));
        assert!(json.contains("\"keyboard\""));
    }
}
