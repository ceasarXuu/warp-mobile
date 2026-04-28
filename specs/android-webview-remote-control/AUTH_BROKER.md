# Android Auth Broker Plan

## Problem

Remote control content can run in Android WebView, but login and registration should not. Google explicitly rejects or downgrades embedded WebView OAuth. GitHub and other providers can also raise risk scoring when login happens inside an embedded browser, especially with VPNs, repeated signups, modified user agents, or unusual cookie behavior.

The current embedded login path is only a compatibility bridge. It keeps OAuth cookies and Warp web cookies in one WebView profile, but it is not a reliable product auth path.

## Target Architecture

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
