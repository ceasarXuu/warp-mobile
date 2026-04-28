# Android Auth Broker Plan

## Problem

Remote control content can run in Android WebView, but login and registration should not. Google explicitly rejects or downgrades embedded WebView OAuth. GitHub and other providers can also raise risk scoring when login happens inside an embedded browser, especially with VPNs, repeated signups, modified user agents, or unusual cookie behavior.

The current embedded login path is only a compatibility bridge. It keeps OAuth cookies and Warp web cookies in one WebView profile, but it is not a reliable product auth path.

## Android-Only Constraint

The hosted Warp backend and `app.warp.dev` cannot be changed. That means Android cannot ask the server for a new mobile-only WebView session bootstrap contract, and it cannot read cookies from Chrome or another browser. Android's browser cookie stores are isolated by design.

Under this constraint, a Custom Tabs login cannot automatically authenticate the existing WebView. If login happens in Chrome, Chrome owns the session. If the remote session runs in WebView, WebView owns a different session. Any solution that depends on moving cookies from Chrome into WebView is not viable.

## Recommended Android-Only Product Shape

Use the trusted browser as the auth and web runtime, and move the native keyboard into the Android input-method layer:

- The app remains the native tab/session launcher and remembers user-created Warp links.
- Unauthenticated links open in Custom Tabs or the user's default browser, not WebView.
- Logged-in remote sessions also run in the trusted browser runtime so the browser session is reused.
- The Astropath-derived keyboard becomes a real Android IME (`InputMethodService`) so it can be selected inside Chrome/Custom Tabs and still provide the terminal key layout.
- The app no longer needs to embed Google/GitHub login, so provider captcha frequency should match normal browser behavior.

This gives up deep DOM visibility from the app shell, but it is the only Android-only path that both avoids WebView OAuth risk and preserves a native keyboard experience without backend changes.

## Aggressive Native Client Option

The source tree includes desktop/CLI device authorization and session-sharing protocol code. A more ambitious Android-only direction is to stop wrapping `app.warp.dev` and build a native remote-session client:

- Use the existing device authorization flow from the Warp auth stack to authenticate the Android app.
- Reuse or port the `session_sharing_protocol` client path for joining a shared remote session.
- Render the terminal/session natively and send input through the protocol instead of the web page.
- Keep the Astropath keyboard as an in-app native component.

This is likely the cleanest end state for "native understands what is happening," but it is a larger product and protocol-porting project. It needs a spike to prove the hosted session-sharing server accepts the same auth and join flows from Android.

## Target Architecture With Backend Support

Use a native auth broker:

- Start login from native UI, not from the WebView login form.
- Launch Google/GitHub auth with Android Custom Tabs or the provider SDK.
- Return through a verified app link or private redirect scheme.
- Exchange the authorization result for a Warp web session through a backend-supported endpoint.
- Bootstrap the WebView with that Warp session, then load the remote session URL.
- Keep WebView persistence for the post-login remote-control experience only.

## Required Backend/Web Contract

The mobile app needs one of these server-supported contracts:

- `authCode -> mobile session bootstrap`: mobile sends an OAuth callback result to Warp, receives a short-lived WebView bootstrap URL or session transfer token.
- `Custom Tab callback -> set WebView session`: callback URL can be loaded in WebView without depending on browser-only cookies.
- `native provider SDK -> Warp session`: mobile receives provider identity token, exchanges it with Warp for a WebView-ready session.

Without one of these contracts, Custom Tabs and WebView will keep separate cookie jars and OAuth state can fail or land on 404.

With the current hosted-only constraint, this section is a future upstream request, not an Android-only implementation dependency.

## Android Implementation Modules

- `auth/AuthBroker`: owns provider choice, Custom Tabs launch, redirect handling, and error states.
- `auth/AuthContinuationStore`: stores pending auth nonce/state before leaving the app.
- `MainActivity`: routes auth redirect intents to `AuthBroker` before session-tab routing.
- `RemoteSessionWebView`: consumes only Warp session bootstrap URLs, not raw provider OAuth URLs.
- `RemoteControlScreen`: shows native login actions when WebView reports unauthenticated state.

## Validation

- Google login does not render inside WebView.
- GitHub login does not render inside WebView.
- No `disallowed_useragent` page appears.
- Captcha rate should match the device browser baseline for the same account/network.
- After successful login, WebView loads the remote session without another login.
- Force-stop and reopen keeps the WebView session.

## Non-Goals

- Do not spoof browser identity to bypass provider checks.
- Do not scrape provider pages.
- Do not share external browser cookies directly with WebView.
- Do not add provider-specific captcha automation.
- Do not keep iterating on embedded Google/GitHub login as the primary path.
