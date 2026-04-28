use std::{env, fs, path::PathBuf};

use mobile_design_tokens::{mobile_design_tokens_from_theme, to_pretty_json};
use warp_core::ui::theme::{AnsiColor, AnsiColors, Details, Fill, TerminalColors, WarpTheme};
use warpui::color::ColorU;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let document = mobile_design_tokens_from_theme("warp-default-dark", &warp_default_dark_theme());
    let json = to_pretty_json(&document)?;

    match env::args_os().nth(1) {
        Some(path) => write_output(PathBuf::from(path), &json)?,
        None => println!("{json}"),
    }

    Ok(())
}

fn write_output(path: PathBuf, json: &str) -> Result<(), Box<dyn std::error::Error>> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, json)?;
    Ok(())
}

fn warp_default_dark_theme() -> WarpTheme {
    WarpTheme::new(
        Fill::Solid(ColorU::from_u32(0x000000ff)),
        ColorU::from_u32(0xffffffff),
        Fill::Solid(ColorU::from_u32(0x19aad8ff)),
        None,
        Some(Details::Darker),
        dark_mode_colors(),
        None,
        Some("Dark".to_owned()),
    )
}

fn dark_mode_colors() -> TerminalColors {
    TerminalColors::new(
        AnsiColors::new(
            AnsiColor::from_u32(0x616161ff),
            AnsiColor::from_u32(0xff8272ff),
            AnsiColor::from_u32(0xb4fa72ff),
            AnsiColor::from_u32(0xfefdc2ff),
            AnsiColor::from_u32(0xa5d5feff),
            AnsiColor::from_u32(0xff8ffdff),
            AnsiColor::from_u32(0xd0d1feff),
            AnsiColor::from_u32(0xf1f1f1ff),
        ),
        AnsiColors::new(
            AnsiColor::from_u32(0x8e8e8eff),
            AnsiColor::from_u32(0xffc4bdff),
            AnsiColor::from_u32(0xd6fcb9ff),
            AnsiColor::from_u32(0xfefdd5ff),
            AnsiColor::from_u32(0xc1e3feff),
            AnsiColor::from_u32(0xffb1feff),
            AnsiColor::from_u32(0xe5e6feff),
            AnsiColor::from_u32(0xfeffffff),
        ),
    )
}
