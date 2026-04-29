# Android Auth Handoff

## Goal

Warp Mobile must not render Warp account login, OAuth provider login, or CAPTCHA pages inside the embedded WebView. The app opens Warp's hosted remote login in the user's browser, receives the desktop-style redirect, stores the returned refresh token locally, and exposes it to `app.warp.dev` through the existing web handoff hook.

## Flow

1. A remote session tab opens without a local refresh token.
2. Android launches `https://app.warp.dev/login/remote?scheme=warposs&state=...` in the browser.
3. Warp's hosted login handles email login, Google login, GitHub login, and CAPTCHA entirely outside the app.
4. The browser redirects to `warposs://auth/desktop_redirect?refresh_token=...&state=...`.
5. Android validates the state, saves the refresh token with Android Keystore encryption, and reloads the selected tab.
6. The WebView installs `window.warpUserHandoff()` at document start for `app.warp.dev`; Warp Web imports the refresh token through its existing handoff path.

## Guardrails

- Do not add broad `https://app.warp.dev` intent filters. They can trap the browser login URL back inside the app.
- Never log the refresh token or state value. Logs only record event names, sanitized host names, and boolean facts.
- WebView provider-login navigation is blocked and externalized. Google, GitHub, and related OAuth hosts should not become top-level pages inside the WebView.
- The WebView keeps cookies, DOM storage, and WebView persistent state enabled so app session data survives process restart.
- The injected JavaScript does not embed the token literal. It calls the Android bridge so token updates after browser login are visible without rebuilding the WebView.

## Test Notes

- Unit tests cover login URL generation and redirect parsing.
- Manual smoke should use a real Warp session link, install the debug APK, create a tab, complete login in the browser, and verify the selected tab reloads without showing the provider login page inside the app.
- Useful log events: `mobile_auth_browser_login_started`, `mobile_auth_redirect_accepted`, `mobile_auth_handoff_reload_requested`, `mobile_auth_handoff_script_installed`, and `mobile_webview_auth_navigation_externalized`.
