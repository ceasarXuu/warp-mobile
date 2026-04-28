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

## 2026-04-29: Android Remote Shell Scaffold And Astropath Keyboard Slice

### Change

- Added `apps/mobile_android` as a native Android Compose/WebView debug shell.
- Added deep-link parsing for `https://app.warp.dev/session/{uuid}` plus a `https://debug.warp.local/session/{uuid}` fake-page route for device smoke tests.
- Added guarded WebView settings, origin allowlist, JavaScript bridge, and structured `WarpMobile` logcat events.
- Exported `warp-mobile-tokens.default-dark.json` into Android assets and loaded it from native UI code.
- Replaced the first simple key bar with an Astropath-derived builtin keyboard slice:
  - `Control` / `Keys` / `Nav` anchors.
  - QWERTY center band, terminal control band, and navigation band.
  - Ctrl/Alt/Shift inactive, one-shot, and locked state model.
  - Astropath-compatible terminal action payloads for raw keys, printable keys, modified keys, and navigation keys.
  - Repeatable press behavior for Backspace/Delete/navigation keys.
  - Token-driven key styling and native haptic feedback.

### Validation

```powershell
cargo fmt --package mobile_design_tokens
cargo test -p mobile_design_tokens
cd D:\warp-mobile\apps\mobile_android
.\gradle.ps1 :app:testDebugUnitTest :app:assembleDebug
cd D:\warp-mobile
adb install -r -t -g apps\mobile_android\app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb shell am start -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity -a android.intent.action.VIEW -d "https://debug.warp.local/session/00000000-0000-0000-0000-000000000000"
adb shell input tap 1035 2646
adb shell input tap 1000 1960
adb shell input tap 735 2070
adb logcat -d -s WarpMobile
```

Result:

- `cargo fmt --package mobile_design_tokens`: passed.
- `cargo test -p mobile_design_tokens`: passed, 3 tests.
- `.\gradle.ps1 :app:testDebugUnitTest :app:assembleDebug`: passed, 9 Android unit tests.
- Real device install: passed on Xiaomi `23078RKD5C`, Android 16.
- Real device launch: app resumed as `dev.warp.mobile.debug/dev.warp.mobile.MainActivity`.
- Real device keyboard smoke: `Enter` and a navigation-band key produced `mobile_keyboard_action_dispatched`, `mobile_bridge_message_sent`, and fake WebView ACK logs.

### Operational Notes

- Android Studio's bundled JBR on this machine is Java 21. Do not force a Gradle Java 17 toolchain lookup here; compile targeting Java 17 works with the bundled JBR.
- Compose requires `android.useAndroidX=true` in `apps/mobile_android/gradle.properties`.
- The local Gradle wrapper helper downloads Gradle 8.10.2 under `%LOCALAPPDATA%\WarpMobile\gradle` and uses Android Studio's JBR when `JAVA_HOME` is unset.
- The first device install can fail with `INSTALL_FAILED_USER_RESTRICTED` on this Xiaomi device until the phone is awake/unlocked and USB install approval is accepted. `adb install -r -t -g ...` succeeded after waking the device.
- Local JVM unit tests that parse `org.json.JSONObject` need `testImplementation("org.json:json:20240303")`; otherwise Android's stubbed `org.json` can fail at runtime in host tests.
