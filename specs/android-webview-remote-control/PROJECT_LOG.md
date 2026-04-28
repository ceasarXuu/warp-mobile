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

## 2026-04-29: Real Warp Session Link Smoke

### Change

- Retested with a real `https://app.warp.dev/session/{uuid}` link instead of the fake debug page.
- Added WebView cookie and third-party cookie acceptance so the embedded app can participate in normal web auth/session flows.
- Expanded WebView navigation allowlist from only `app.warp.dev` to Warp-controlled `*.app.warp.dev` subdomains plus `accounts.google.com` for auth navigation.
- Added host/path/main-frame fields to HTTP error logs so real-link failures identify the failing service without logging full URLs or tokens.
- Set `MainActivity` to `singleTask` so repeated real App Link launches reuse the same shell instead of stacking stale debug and real-link activities.
- Applied safe drawing insets to the native shell so the status bar no longer overlaps the session title on Android 15+ edge-to-edge devices.

### Validation

```powershell
cd D:\warp-mobile\apps\mobile_android
.\gradle.ps1 :app:testDebugUnitTest :app:assembleDebug
cd D:\warp-mobile
adb install -r -t -g apps\mobile_android\app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb shell am start -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity -a android.intent.action.VIEW -d "https://app.warp.dev/session/{uuid}"
adb logcat -d -s WarpMobile
```

Result:

- Build and Android unit tests passed.
- Real App Link parse and WebView main document load succeeded.
- Actual remote session content did not become controllable yet. The loaded Warp web app returned:
  - `401` from `app.warp.dev/auth/session`
  - `401` from `sessions.app.warp.dev/sessions/{uuid}`
- Before the auth-origin change, the same flow also attempted to navigate to `accounts.google.com` and was blocked by the stricter first allowlist.

### Operational Notes

- A real-link smoke must check both WebView rendering and `WarpMobile` logcat. A blank white WebView can still mean the main SPA loaded but downstream auth/session API calls returned 401.
- Do not treat fake bridge ACK as proof that production `app.warp.dev/session/{uuid}` works. The production gate is a real session API response plus bridge/capability events from Warp web.
- Do not commit full real session URLs in diagnostics; use `{uuid}` or the app's `session_id_hash`.

## 2026-04-29: Astropath Keyboard Interaction Parity

### Change

- Restored the Astropath builtin keyboard band model in Android native code:
  - `leftPeek` / `center` / `rightPeek` anchors use Astropath's `0.42` side-band width factor.
  - Horizontal drag resolves with Astropath's viewport-based `0.18` threshold.
  - Long drags can skip to the closest directional anchor instead of only moving one step.
  - Drag start, completion, cancel, and anchor-change events are logged with offsets and session hash.
- Added exclusive keyboard modes:
  - `builtin` owns the custom terminal keyboard and hides the system IME.
  - `systemIme` owns a focused native text field, shows the system keyboard, and keeps a terminal accessory row.
  - Header buttons switch between the two modes without rendering both input surfaces at once.
- Kept native haptic feedback centralized in shared key chrome; repeatable keys only trigger haptics on the initial press.
- Split keyboard chrome, bands, mode panel, and state tests into separate files so Android source files stay under the 500-line limit.

### Validation

```powershell
cd D:\warp-mobile\apps\mobile_android
.\gradle.ps1 :app:testDebugUnitTest
.\gradle.ps1 :app:assembleDebug
cd D:\warp-mobile
adb install -r -t -g apps\mobile_android\app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb shell am start -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity -a android.intent.action.VIEW -d "https://app.warp.dev/session/{uuid}"
adb shell input swipe 900 2200 250 2200 400
adb shell input swipe 250 2200 900 2200 400
adb shell input tap 1050 1960
adb shell input tap 142 1279
adb logcat -d -s WarpMobile
```

Result:

- `.\gradle.ps1 :app:testDebugUnitTest`: passed, including anchor resolution tests for threshold, clamping, and long-drag skip behavior.
- `.\gradle.ps1 :app:assembleDebug`: passed.
- Real device install and launch succeeded against the user-provided real session link.
- Real session still reaches the expected unauthenticated web state:
  - `401` from `app.warp.dev/auth/session`
  - `401` from `sessions.app.warp.dev/sessions/{uuid}`
- Real device keyboard smoke confirmed:
  - left swipe from center logged `mobile_keyboard_band_drag_completed` and `mobile_keyboard_anchor_changed` with `resolved_anchor=rightpeek`.
  - right swipe from right peek logged `resolved_anchor=center`.
  - `System` switch logged `mobile_keyboard_mode_changed` with `keyboard_mode=systemime`.
  - `Built-in` switch logged `mobile_keyboard_mode_changed` with `keyboard_mode=builtin`.
  - right-peek UI hierarchy exposed the expected printable symbol grid and arrow grid, while center mode exposed the full QWERTY band.
  - UI hierarchy in system mode exposed `Built-in`, `Ctrl`, `Alt`, `Esc`, `Tab`, `Enter`, navigation keys, `Bksp`, and the focused `System keyboard input` field.

### Operational Notes

- Astropath's switch threshold is based on the full viewport width, not the side-band width. Using the side-band width makes accidental anchor switches too easy.
- Compose constrains oversized children differently from Flutter's `SizedBox` inside `ClipRect`; the sliding band row must use a required oversized width, start alignment, and `clipToBounds()` to keep center/right bands in the expected positions.
- When testing edge anchors, swiping further outward should log a drag cancel or remain on the same anchor; test both outward and inward directions.
- Use logcat mode events plus UI hierarchy dumps to verify builtin/system IME exclusivity. A mode log alone does not prove the other input surface was removed.

## 2026-04-29: Browser Pane Keyboard Squeeze

### Change

- Wrapped the WebView in a clipped weighted browser pane so native keyboard surfaces are normal layout siblings, not overlays.
- Added `imePadding()` to the system IME panel so the accessory row and Android system keyboard jointly reduce browser height.
- Passed a modifier through `TerminalKeyboardBar` so the shell can measure the top of either builtin or system keyboard mode.
- Added debounced `mobile_shell_keyboard_layout_measured` logs with `browser_bottom_px`, `keyboard_top_px`, and `gap_px`.

### Validation

```powershell
cd D:\warp-mobile\apps\mobile_android
.\gradle.ps1 :app:testDebugUnitTest :app:assembleDebug
cd D:\warp-mobile
adb install -r -t -g apps\mobile_android\app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb shell am start -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity -a android.intent.action.VIEW -d "https://app.warp.dev/session/{uuid}"
adb shell input tap 1050 1960
adb shell input tap 142 1290
adb logcat -d -s WarpMobile
```

Result:

- Build and Android unit tests passed.
- Real device builtin mode logged `browser_bottom_px=1896`, `keyboard_top_px=1896`, `gap_px=0`.
- Real device system IME mode logged `browser_bottom_px=1219`, `keyboard_top_px=1219`, `gap_px=0` while the Android system keyboard was visible.
- Switching back to builtin mode returned to `browser_bottom_px=1896`, `keyboard_top_px=1896`, `gap_px=0`.

### Operational Notes

- A zero gap between browser bottom and keyboard top is the runtime invariant for "squeeze instead of cover".
- Capture screenshots together with `mobile_shell_keyboard_layout_measured`; visual inspection alone can miss a WebView that is still drawing behind a Compose sibling.

## 2026-04-29: Persistent Keyboard Mode Toggle

### Change

- Kept the keyboard mode switch available in both modes:
  - builtin mode keeps the `System` button in the keyboard header.
  - system IME mode keeps a single full-width `Built-in keyboard` button above the Android system keyboard.
- Removed the visible shortcut rows and visible input buffer from system IME mode.
- Kept an invisible focused text field in system IME mode so Android can show the system keyboard and still route committed text to the terminal bridge.

### Validation

```powershell
cd D:\warp-mobile\apps\mobile_android
.\gradle.ps1 :app:testDebugUnitTest :app:assembleDebug
cd D:\warp-mobile
adb install -r -t -g apps\mobile_android\app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb shell am start -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity -a android.intent.action.VIEW -d "https://app.warp.dev/session/{uuid}"
adb shell input tap 1050 1960
adb shell input tap 600 1520
adb logcat -d -s WarpMobile
```

Result:

- Build and Android unit tests passed.
- Real device system IME mode displayed only the persistent `Built-in keyboard` switch above the Android keyboard; no native shortcut row or visible input buffer remained.
- Real device mode switch logged `keyboard_mode=systemime`, then `keyboard_mode=builtin`.
- Stable layout measurement stayed squeezed:
  - system IME: `browser_bottom_px=1450`, `keyboard_top_px=1450`, `gap_px=0`
  - builtin: `browser_bottom_px=1896`, `keyboard_top_px=1896`, `gap_px=0`
