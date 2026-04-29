# Android Auth Handoff

## Goal

Warp Mobile must not render Warp account login, OAuth provider login, or CAPTCHA pages inside the embedded WebView. The app opens Warp's hosted remote login in the user's browser, receives the desktop-style redirect, stores the returned refresh token locally, and exposes it to `app.warp.dev` through the existing web handoff hook.

## Flow

1. A remote session tab opens without a local refresh token.
2. Android launches `https://app.warp.dev/login/remote?scheme=warposs&state=...` in the browser.
3. Warp's hosted login handles email login, Google login, GitHub login, and CAPTCHA entirely outside the app.
4. The browser redirects to `warposs://auth/desktop_redirect?refresh_token=...&state=...`.
5. Android validates the state, saves the refresh token with Android Keystore encryption, and reloads the selected tab.
6. The WebView installs a document-start bootstrap for `app.warp.dev`.
7. The bootstrap exchanges the stored Firebase refresh token for an ID token through Firebase Secure Token, posts that ID token to `/api/v1/oauth/session`, and reloads the current page after the session cookie is established.
8. The same bootstrap also exposes `window.warpUserHandoff()` so Warp's inner WASM client can receive the refresh token after the React host is logged in.

## Guardrails

- Do not add broad `https://app.warp.dev` intent filters. They can trap the browser login URL back inside the app.
- Never log the refresh token or state value. Logs only record event names, sanitized host names, and boolean facts.
- WebView provider-login navigation is blocked and externalized. Google, GitHub, and related OAuth hosts should not become top-level pages inside the WebView.
- The WebView keeps cookies, DOM storage, and WebView persistent state enabled so app session data survives process restart.
- The injected JavaScript does not embed the token literal. It calls the Android bridge so token updates after browser login are visible without rebuilding the WebView.
- `window.warpUserHandoff` is installed as an accessor. Warp Web's own bundle also assigns this symbol, so the Android accessor must accept that assignment and keep it as a fallback instead of making the property read-only.
- The Firebase API key used by the bootstrap is the public web key from the hosted Warp bundle; it is not a secret. Do not log refresh tokens or ID tokens.
- If `/api/v1/oauth/session` rejects the exchanged ID token with `Recent sign in required`, the WebView asks Android to start browser login again. This keeps reauthentication in the browser instead of exposing the login form inside the app.

## Test Notes

- Unit tests cover login URL generation and redirect parsing.
- Manual smoke should use a real Warp session link, install the debug APK, create a tab, complete login in the browser, and verify the selected tab reloads without showing the provider login page inside the app.
- Useful log events: `mobile_auth_browser_login_started`, `mobile_auth_redirect_accepted`, `mobile_auth_handoff_reload_requested`, `mobile_auth_handoff_script_installed`, and `mobile_webview_auth_navigation_externalized`.
