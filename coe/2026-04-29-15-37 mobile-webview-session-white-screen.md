# Problem P-001: Mobile WebView session white screen after login
- Status: open
- Created: 2026-04-29 15:37
- Updated: 2026-04-29 16:01
- Objective: Make a valid Warp remote session URL render in the Android embedded WebView after browser login.
- Symptoms:
  - User reports browser login appears to succeed.
  - Opening `https://app.warp.dev/session/a9ec155a-27b8-4490-87d2-ba3a023a9f72` in the app shows a blank white page.
  - The same link works in the system browser.
- Expected behavior:
  - The session page renders inside the app after login handoff.
- Actual behavior:
  - The WebView page is blank white with no visible content.
- Impact:
  - Core remote-control flow is blocked on Android.
- Reproduction:
  - Install debug app, complete browser login, open the provided session URL in the app.
- Environment:
  - Repo `D:\warp-mobile`, branch `master`, commit `8811c33`; Android debug app `dev.warp.mobile.debug`; physical device `ONNZ95CAEMMZSKTS`.
- Known facts:
  - System browser can render the same session URL.
  - WebView starts loading the provided session URL, logs two JavaScript console errors, then finishes the page while the visible content remains white.
  - The blocking console error is `Cannot assign to read only property 'warpUserHandoff'`; hosted Warp Web assigns `window.warpUserHandoff` inside its own bundle.
  - After the read-only property fix, the WebView renders the Warp login page instead of staying white.
  - The Android bootstrap can exchange the stored refresh token with Firebase Secure Token, but `/api/v1/oauth/session` rejects the resulting ID token with `Recent sign in required`.
- Ruled out:
  - none
- Fix criteria:
  - The provided valid session URL renders inside Android WebView on the connected device after a browser login or required reauthentication.
- Current conclusion: The original white screen is fixed by making `warpUserHandoff` assignable. The remaining login page is an authentication freshness problem: Warp's web session endpoint requires a recent sign-in, so stale browser-redirect tokens must trigger browser reauthentication.
- Related hypotheses:
  - H-001
  - H-002
- Resolution basis:
  - not satisfied
- Close reason:
  - not closed

## Hypothesis H-002: Stored redirect token is valid but too stale for web session creation
- Status: confirmed
- Parent: P-001
- Claim: A refresh token from the browser redirect can be valid for Firebase token refresh while still failing Warp Web session creation because `/api/v1/oauth/session` requires recent sign-in.
- Layer: sub-cause
- Factor relation: single
- Depends on:
  - H-001
- Rationale:
  - After fixing the WebView script crash, the app reaches the login screen and logs authenticated bootstrap attempts, but the session endpoint rejects the exchanged ID token.
- Falsifiable predictions:
  - If true: Secure Token exchange succeeds, but session creation returns 401 with `Recent sign in required`.
  - If false: Secure Token exchange should fail, or session creation should return a different error.
- Verification plan:
  - Capture WebView bootstrap logs while opening the provided session URL with the stored token.
- Related evidence:
  - E-003
- Conclusion: Confirmed by logs showing token exchange success and session creation failure with `Recent sign in required`.
- Next step: route this case back to browser login so the user can complete recent authentication outside the app, then reload the WebView with a fresh token.
- Blocker:
  - Full validation requires completing the browser reauthentication flow on the device.
- Close reason:
  - not closed

## Hypothesis H-001: WebView auth handoff or navigation policy leaves the session document uninitialized
- Status: confirmed
- Parent: P-001
- Claim: The blank page happens because the embedded WebView cannot complete Warp Web initialization after browser login, either because auth handoff is missing at document start or because a required navigation/resource is blocked.
- Layer: root-cause
- Factor relation: any_of
- Depends on:
  - none
- Rationale:
  - The same URL works in system browser, and the recent mobile change altered WebView auth injection and blocked provider-login navigations.
- Falsifiable predictions:
  - If true: App logs or WebView console should show auth handoff, navigation, JavaScript, HTTP, or blocked-host events around the blank page.
  - If false: Logs should show a successfully loaded and initialized session page, pushing investigation toward layout/composition rendering.
- Verification plan:
  - Capture filtered logcat while launching the provided session URL in the app.
- Related evidence:
  - E-001
  - E-002
- Conclusion: Confirmed by the detailed console message and hosted bundle code; the property shape must permit assignment while preserving Android token handoff.
- Next step: validate the accessor-based handoff script on the device.
- Blocker:
  - none
- Close reason:
  - not closed

## Evidence E-001: WebView loads the session but reports console errors and renders white
- Related hypotheses:
  - H-001
- Direction: supports
- Type: log
- Source: `adb logcat` plus `adb exec-out screencap` on device `ONNZ95CAEMMZSKTS`
- Raw content:
  ```text
  04-29 15:39:16.264 I/WarpMobile: {"event":"mobile_webview_load_started","session_id_hash":"662e4b7a3fbcf5ea3d02dc000d4e4635d975d649d4a5530ccd4e08dcc47a5f41"}
  04-29 15:39:17.090 I/WarpMobile: {"event":"mobile_webview_console","level":"ERROR","line":"1494"}
  04-29 15:39:17.139 I/WarpMobile: {"event":"mobile_webview_console","level":"ERROR","line":"5"}
  04-29 15:39:18.268 I/WarpMobile: {"event":"mobile_webview_persistent_state_flushed","reason":"page_finished"}
  Screenshot shows tab strip and keyboard bar with a fully white WebView area.
  ```
- Interpretation: The app is not merely failing to navigate; the session document loads far enough to run JavaScript and then fails before rendering useful UI.
- Time: 2026-04-29 15:39

## Evidence E-002: Read-only warpUserHandoff blocks Warp Web bundle initialization
- Related hypotheses:
  - H-001
- Direction: supports
- Type: log
- Source: `adb logcat` after adding sanitized console message logging; `https://app.warp.dev/static/js/index.js`
- Raw content:
  ```text
  mobile_webview_console ERROR line 1494:
  Uncaught TypeError: Cannot assign to read only property 'warpUserHandoff' of object '#<Window>'

  Hosted bundle snippet:
  window.warpUserHandoff=function(){let e=LY(QX);return e.currentUser?e.currentUser.refreshToken:null};
  ```
- Interpretation: Android installed the handoff hook as a non-writable data property. Warp Web expects to assign the same global, so the assignment throws and interrupts the app bundle before rendering.
- Time: 2026-04-29 15:43

## Evidence E-003: Web session creation rejects stale ID token as not recently signed in
- Related hypotheses:
  - H-002
- Direction: supports
- Type: log
- Source: `adb logcat` after WebView session bootstrap implementation
- Raw content:
  ```text
  mobile_auth_handoff_token_requested token_present=true
  mobile_auth_handoff_token_updated
  mobile_webview_console LOG: Warp Android auth bootstrap token exchanged astral-field-294621 https://securetoken.google.com/astral-field-294621
  mobile_webview_http_error path=/api/v1/oauth/session status=401
  mobile_webview_console WARNING: Warp Android auth bootstrap session failed 401 {"error":"Recent sign in required"}
  mobile_auth_handoff_browser_login_requested reason=recent_sign_in_required
  mobile_auth_browser_login_started
  ```
- Interpretation: The stored token is structurally valid enough to exchange with Firebase, but Warp's hosted session endpoint refuses it for not being a recent sign-in. Android must open browser reauth rather than keeping the login form in WebView.
- Time: 2026-04-29 16:00
