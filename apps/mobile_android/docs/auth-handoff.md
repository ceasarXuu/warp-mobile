# Android Auth Handoff

## Goal

Warp Mobile must not render Warp account login, OAuth provider login, or CAPTCHA pages inside the embedded WebView. The app opens Warp's hosted remote login in the user's browser, receives the desktop-style redirect, stores the returned refresh token locally, and exposes it to `app.warp.dev` through the existing web handoff hook.

## Flow

1. A remote session tab opens without a local refresh token.
2. Android launches `https://app.warp.dev/login/remote?scheme=warposs&state=...` in the browser for first-time login.
3. Warp's hosted login handles email login, Google login, GitHub login, and CAPTCHA entirely outside the app.
4. The browser redirects to `warposs://auth/desktop_redirect?refresh_token=...&state=...`.
5. Android validates the state, saves the refresh token with Android Keystore encryption, and reloads the selected tab.
6. The WebView installs a document-start bootstrap for `app.warp.dev`.
7. The bootstrap exchanges the stored Firebase refresh token for an ID token through Firebase Secure Token, posts that ID token to `/api/v1/oauth/session`, and reloads the current page after the session cookie is established.
8. The same bootstrap also exposes `window.warpUserHandoff()` so Warp's inner WASM client can receive the refresh token after the React host is logged in.
9. If the hosted session endpoint rejects the token with `Recent sign in required`, Android launches `https://app.warp.dev/login_options/remote?scheme=warposs&state=...`. The hosted app logs out the existing browser-side Warp user before showing provider choices, so the returned token should carry a fresh provider sign-in.
10. If a fresh browser redirect still fails with `Recent sign in required`, Android suppresses further automatic browser launches and leaves the tab behind a native sign-in gate. The user can tap `Sign in` to deliberately retry, but the app must not enter a browser/App loop.

## Guardrails

- Do not add broad `https://app.warp.dev` intent filters. They can trap the browser login URL back inside the app.
- Register the desktop auth schemes that hosted Warp may emit (`warp`, `warpbeta`, `warpcanary`, `warppreview`, `openwarp`, and `warposs`) only for `/auth/desktop_redirect`. Some hosted fallback pages default to `warp://...` even though Android starts login with `scheme=warposs`.
- Never log the refresh token or state value. Logs only record event names, sanitized host names, and boolean facts.
- WebView provider-login navigation is blocked and externalized. Google, GitHub, and related OAuth hosts should not become top-level pages inside the WebView.
- The WebView keeps cookies, DOM storage, and WebView persistent state enabled so app session data survives process restart.
- The injected JavaScript does not embed the token literal. It calls the Android bridge so token updates after browser login are visible without rebuilding the WebView.
- `window.warpUserHandoff` is installed as an accessor. Warp Web's own bundle also assigns this symbol, so the Android accessor must accept that assignment and keep it as a fallback instead of making the property read-only.
- The Firebase API key used by the bootstrap is the public web key from the hosted Warp bundle; it is not a secret. Do not log refresh tokens or ID tokens.
- If `/api/v1/oauth/session` rejects the exchanged ID token with `Recent sign in required`, the WebView asks Android for a browser login with that exact reason. Native uses the fresh login route once, then suppresses repeated automatic launches if the fresh redirect still fails.
- When native considers embedded auth unavailable, it does not create the WebView and does not show the terminal keyboard. The user sees only the native sign-in gate, so Warp login forms, OAuth pages, and CAPTCHA never appear inside the app.
- Browser launch logs include `reason`, `force_fresh`, and suppression policy fields. These are safe operational diagnostics; tokens and state remain redacted.

## Test Notes

- Unit tests cover login URL generation and redirect parsing.
- Manual smoke should use a real Warp session link, install the debug APK, create a tab, complete login in the browser, and verify the selected tab reloads without showing the provider login page inside the app.
- Useful log events: `mobile_auth_browser_login_started`, `mobile_auth_browser_login_suppressed`, `mobile_auth_redirect_accepted`, `mobile_auth_embedded_gate_changed`, `mobile_auth_handoff_reload_requested`, `mobile_auth_handoff_script_installed`, and `mobile_webview_auth_navigation_externalized`.
