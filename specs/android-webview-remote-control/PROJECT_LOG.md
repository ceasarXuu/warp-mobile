# Android WebView Remote Control Project Log

## 2026-04-29: Mobile Design Token Exporter

### Change

- Added `crates/mobile_design_tokens`.
- The crate exports a versioned `MobileDesignTokenDocument` from `warp_core::ui::theme::WarpTheme`.
- Exported token groups:
  - core colors and surface tokens
  - terminal ANSI colors
  - Warp button variants: Primary, Secondary, Naked, Disabled, DangerPrimary
  - keyboard visual mapping for printable/tool/primary/disabled keys
- The implementation preserves Warp `Fill` kind and includes mobile solid fallback colors for midpoint, top-biased, and right-biased rendering.

### Validation

```powershell
cargo fmt --package mobile_design_tokens
cargo test -p mobile_design_tokens
```

Result:

- `cargo fmt --package mobile_design_tokens`: passed.
- `cargo test -p mobile_design_tokens`: passed, 3 tests.

### Operational Note

Do not run first-time `cargo fmt` and `cargo test` in parallel on this Windows machine when rustup may need to install toolchain components. Parallel rustup downloads can contend on the same cache file and fail with a component rename error. Run the first toolchain-touching command serially, then parallelize later checks after the toolchain is warm.
