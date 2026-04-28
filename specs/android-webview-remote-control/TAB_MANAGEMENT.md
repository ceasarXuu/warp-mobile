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

Google and GitHub login currently stay in the embedded browser only as a short-term compatibility path so OAuth state cookies and final Warp session cookies remain in the same WebView profile. This is not the long-term auth architecture: embedded OAuth increases provider risk scoring and can trigger `disallowed_useragent`, captcha, or account verification. The long-term path is a native auth broker that starts OAuth in Android Custom Tabs or provider SDKs, receives the redirect through a verified app link or private redirect scheme, and exchanges the result into a Warp web session that can be injected or resumed in the WebView without re-authenticating inside the WebView.

Do not add further WebView user-agent spoofing or provider-specific captcha workarounds. Those are brittle and make the client look less trustworthy. Future implementation should move login/registration out of `RemoteSessionWebView` while keeping the remote-control surface in WebView after a valid Warp session exists.

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
- `mobile_auth_continuation_received`
- `mobile_auth_continuation_rejected`
- `mobile_webview_url_load_requested`

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
- tapping Google login does not show Google's `disallowed_useragent` page, but captcha frequency is still expected to be higher than a system-browser auth flow.
- tapping GitHub login does not get blocked by the embedded WebView host allowlist, but provider risk checks can still be stricter than a system-browser auth flow.
- native auth broker follow-up tests should verify Custom Tabs/provider SDK login, redirect return, WebView session bootstrap, and app restart persistence.
