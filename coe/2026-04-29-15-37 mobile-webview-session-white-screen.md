# Problem P-001: Mobile WebView session white screen after login
- Status: open
- Created: 2026-04-29 15:37
- Updated: 2026-04-29 19:05
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
  - A browser redirect accepted immediately before a `Recent sign in required` failure can repeat, causing Android to reopen the browser in a loop.
  - Hosted Warp Web's normal `/login/remote` route can redirect an already logged-in browser user to `/logged_in/remote` without forcing a fresh provider login; `/login_options/remote` goes through the hosted login-options route, which logs out a current non-anonymous browser user before provider sign-in.
  - The Android fix now opens a force-fresh browser login on the first `Recent sign in required` failure, gates the embedded WebView natively, and does not auto-launch the browser after a process restart when a refresh token already exists.
  - The installed app resolves `warposs://auth/desktop_redirect...`, but did not resolve `warp://auth/desktop_redirect...`; hosted Warp's redirect helper defaults to `warp` when the `scheme` query param is missing.
  - After the fix, Android resolves `warp://auth/desktop_redirect...` to the app and `WarpLoginBroker` routes it through auth redirect handling instead of session-link parsing.
- Ruled out:
  - none
- Fix criteria:
  - The provided valid session URL renders inside Android WebView on the connected device after a browser login or required reauthentication.
- Current conclusion: The original white screen is fixed by making `warpUserHandoff` assignable. The login handoff must support both the Android-specific `warposs` scheme and hosted Warp's default desktop `warp` scheme because browser fallback pages can emit either.
- Related hypotheses:
  - H-001
  - H-002
  - H-003
  - H-004
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

## Hypothesis H-003: Browser login reentry lacks a native fresh-auth state machine
- Status: confirmed
- Parent: P-001
- Claim: The loop happens because Android treats every WebView `recent_sign_in_required` callback as permission to open `/login/remote` again, even immediately after a browser redirect; the hosted route can return the existing browser user's token without forcing fresh provider login.
- Layer: root-cause
- Factor relation: all_of
- Depends on:
  - H-002
- Rationale:
  - The user reports a browser/App loop after a successful browser redirect, and previous evidence shows `/api/v1/oauth/session` requires recent sign-in.
- Falsifiable predictions:
  - If true: logs should show repeated `mobile_auth_redirect_accepted`, `/api/v1/oauth/session` 401 `Recent sign in required`, and `mobile_auth_browser_login_started` cycles.
  - If false: the callback should not repeat after redirects, or browser reentry should use a route that guarantees fresh sign-in.
- Verification plan:
  - Inspect device logs around the reported loop and inspect hosted Warp Web route behavior for `/login/remote` and `/login_options/remote`.
- Related evidence:
  - E-004
  - E-005
- Conclusion: Confirmed by device logs showing the exact redirect-failure-reopen cycle and by hosted bundle code showing `/login/remote` can proceed to `/logged_in/remote` for an already logged-in browser user. The native reentry policy has build and smoke validation, with full resolution still waiting on a completed fresh browser login redirect from the user.
- Next step: User completes the fresh browser login, then verify the app accepts the redirect and the session page renders without reopening the browser.
- Blocker:
  - none
- Close reason:
  - not closed

## Hypothesis H-004: Hosted fallback uses an unregistered desktop scheme
- Status: confirmed
- Parent: P-001
- Claim: The browser `Take me to Warp` button can fail to launch Android because the hosted page emits `warp://auth/desktop_redirect...`, while Android only registered and parsed `warposs://auth/desktop_redirect...`.
- Layer: interaction
- Factor relation: single
- Depends on:
  - H-003
- Rationale:
  - The user reports that clicking `Take me to Warp` after browser login does not open the app, and the hosted bundle has multiple desktop-scheme redirect paths.
- Falsifiable predictions:
  - If true: Android resolves `warposs://auth/desktop_redirect...` but not `warp://auth/desktop_redirect...`, and hosted code defaults to `warp` if `scheme` is absent.
  - If false: Android should already resolve `warp://...`, or hosted code should never emit that scheme for this flow.
- Verification plan:
  - Query Android activity resolution for both schemes and inspect hosted redirect helper code.
- Related evidence:
  - E-006
  - E-007
- Conclusion: Confirmed by device resolver output and hosted bundle code. Fixed by registering and parsing hosted desktop redirect schemes for the same `/auth/desktop_redirect` path, keeping pending-state validation unchanged.
- Next step: Validate with the user's real browser `Take me to Warp` click after a fresh login.
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

## Evidence E-004: Device logs show redirect and recent-sign-in browser reopen cycle
- Related hypotheses:
  - H-003
- Direction: supports
- Type: log
- Source: `adb logcat -d -v time WarpMobile:I chromium:W cr_Console:V AndroidRuntime:E *:S` on device `ONNZ95CAEMMZSKTS`; hosted `https://app.warp.dev/static/js/index.js`
- Raw content:
  ```text
  04-29 18:36:01.814 mobile_auth_redirect_accepted user_uid_present=true
  04-29 18:36:03.158 mobile_webview_http_error path=/api/v1/oauth/session status=401
  04-29 18:36:03.159 Warp Android auth bootstrap session failed 401 {"error":"Recent sign in required"}
  04-29 18:36:03.160 mobile_auth_handoff_browser_login_requested reason=recent_sign_in_required
  04-29 18:36:03.160 mobile_auth_browser_login_started
  04-29 18:36:06.254 mobile_auth_redirect_accepted user_uid_present=true
  04-29 18:36:07.622 mobile_webview_http_error path=/api/v1/oauth/session status=401
  04-29 18:36:07.626 mobile_auth_browser_login_started

  Hosted bundle route findings:
  - `/login/remote` is handled by the login route and can navigate to `/logged_in/remote?...` when a non-anonymous browser user already exists.
  - `/login_options/remote` is handled by the login-options route, which logs out a current non-anonymous browser user before showing login options.
  ```
- Interpretation: The loop is not a WebView rendering issue. Android needs a native browser-auth state machine with a fresh-login mode and a post-fresh-redirect loop breaker.
- Time: 2026-04-29 18:46

## Evidence E-005: Native auth gate and fresh-browser path smoke validated on device
- Related hypotheses:
  - H-003
- Direction: supports
- Type: fix-validation
- Source: Gradle build/tests, APK install, `adb logcat`, and screenshots under `apps/mobile_android/build/verification/`
- Raw content:
  ```text
  Gradle: compileDebugKotlin testDebugUnitTest assembleDebug BUILD SUCCESSFUL
  adb install -r app-debug.apk: Success

  04-29 18:46:32.111 mobile_auth_embedded_gate_changed required=true reason=recent_sign_in_required
  04-29 18:46:32.112 mobile_auth_browser_login_started force_fresh=true reason=recent_sign_in_required

  After force-stop and relaunch:
  04-29 18:48:34.168 mobile_shell_created
  04-29 18:48:34.210 mobile_tab_store_loaded tab_count=14
  No mobile_auth_browser_login_started event was emitted after relaunch.

  Screenshot `auth-gate-smoke-2.png` shows the tab strip plus native sign-in gate, with no Web login page and no terminal keyboard.
  ```
- Interpretation: The implemented state machine chooses the fresh hosted login route for a stale token, hides embedded Web login UI behind native chrome, and avoids automatic browser relaunch after process restart when a refresh token already exists.
- Time: 2026-04-29 18:49

## Evidence E-006: Android only resolves warposs while hosted can default to warp
- Related hypotheses:
  - H-004
- Direction: supports
- Type: experiment
- Source: `adb shell cmd package resolve-activity --brief ...`; hosted `https://app.warp.dev/static/js/index.js`
- Raw content:
  ```text
  warposs://auth/desktop_redirect?refresh_token=x&state=y
  -> dev.warp.mobile.debug/dev.warp.mobile.MainActivity

  warp://auth/desktop_redirect?refresh_token=x&state=y
  -> No activity found

  Hosted helper:
  function V0(e){return(`warp,warpbeta,warpcanary,warppreview,openwarp,warposs`.split(`,`).includes(e)?e:`warp`).trim()}
  function H0(e,t){return`${V0(e.get(`scheme`)||``)}://auth/desktop_redirect?...`}
  ```
- Interpretation: If the browser page loses the `scheme=warposs` query parameter or uses a fallback route, `Take me to Warp` can generate `warp://...`, which Android previously did not handle.
- Time: 2026-04-29 19:04

## Evidence E-007: Android accepts hosted fallback warp scheme after fix
- Related hypotheses:
  - H-004
- Direction: supports
- Type: fix-validation
- Source: Gradle build/tests, APK install, Android resolver, and `adb shell am start` on device `ONNZ95CAEMMZSKTS`
- Raw content:
  ```text
  Gradle: compileDebugKotlin testDebugUnitTest assembleDebug BUILD SUCCESSFUL
  adb install -r app-debug.apk: Success

  resolve-activity warp://auth/desktop_redirect?refresh_token=x&state=y
  -> dev.warp.mobile.debug/dev.warp.mobile.MainActivity

  adb shell am start ... warp://auth/desktop_redirect?refresh_token=x&state=y
  mobile_shell_created
  mobile_auth_redirect_rejected reason=state_mismatch
  mobile_tab_store_loaded tab_count=10
  ```
- Interpretation: The fallback `warp://` scheme now launches the app and reaches auth redirect handling. The expected fake-state rejection proves pending-state validation still protects the token handoff.
- Time: 2026-04-29 19:05
