# Android Remote Tab Management

## Product Contract

The mobile shell starts on a welcome surface when there are no saved remote tabs and no incoming App Link. Tapping `New tab` opens a native link prompt. Every user-created tab must come from an explicit URL entry so the app never silently falls back to demo data.

The shell persists the currently open tabs and selected tab locally. On the next launch without an App Link, it restores the same tab list and selects the last active tab. An incoming App Link creates a new tab and preserves existing saved tabs.

## Native State Model

- `RemoteScreenState.Welcome`: no saved tabs are available; the only primary action is creating a tab.
- `RemoteScreenState.Ready`: owns a list of `RemoteTab` objects plus the selected tab id.
- `RemoteTab`: stores a stable tab id, the parsed `SessionLaunchRequest`, and creation time.
- `RemoteTabStore`: serializes tab metadata to Android `SharedPreferences`.

Tab persistence stores the normalized `loadUrl`, not WebView DOM state. Auth and web session continuity are shared through Android WebView's process-wide cookie and storage profile, so tabs do not require separate login.

The embedded browser profile is also expected to survive app restarts. `RemoteSessionWebView` enables DOM storage and Web SQL database storage for web auth flows, accepts first-party and third-party cookies, and flushes the cookie jar after page finishes plus Activity pause/destroy. Do not add per-tab or per-launch isolated WebView data directories unless the product explicitly needs separate identities.

Google login is allowed inside the embedded browser. The navigation allowlist includes Warp app/session hosts plus Google OAuth/login support domains, and WebView popup windows are captured back into the visible remote WebView so OAuth flows that use `window.open` do not land in an invisible blank WebView.

## UI Behavior

- The top native strip shows one compact tab pill per open remote tab.
- The selected pill uses the Warp token accent/outline treatment.
- The `+` control is always available in the tab strip and opens the native URL dialog.
- Invalid links keep the dialog open and show the parser reason without changing the current tab.

## Logging

The feature emits structured `WarpMobile` events:

- `mobile_tab_welcome_shown`
- `mobile_tab_store_loaded`
- `mobile_tab_store_saved`
- `mobile_tab_created`
- `mobile_tab_selected`
- `mobile_tab_create_failed`
- `mobile_tab_restore_dropped`
- `mobile_webview_persistent_state_flushed`
- `mobile_webview_popup_requested`
- `mobile_webview_popup_url_accepted`
- `mobile_webview_popup_url_blocked`

These logs are required for device smoke tests because WebView visual state alone cannot prove tab restore or shared auth behavior.

## Validation

Required checks for tab changes:

```powershell
cd D:\warp-mobile\apps\mobile_android
.\gradle.ps1 :app:testDebugUnitTest :app:assembleDebug
```

Real-device smoke should verify:

- fresh launch shows the welcome surface,
- `New tab` opens a URL prompt,
- a real `https://app.warp.dev/session/{uuid}` link creates a tab,
- launching the app again restores the tab strip and selected tab,
- creating another tab does not require another login after the first tab has authenticated.
- force-stopping and reopening the app after web login preserves the embedded browser session.
- tapping Google login navigates to a visible Google sign-in page instead of a blank screen, and logcat does not show `mobile_webview_popup_url_blocked` for expected Google OAuth hosts.
