# Android Device Smoke

## Commands

Build, test, and package from `apps/mobile_android`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File gradle.ps1 compileDebugKotlin testDebugUnitTest assembleDebug
```

Install the debug APK without clearing persisted WebView/auth data:

```powershell
C:/Users/77585/AppData/Local/Android/Sdk/platform-tools/adb.exe install -r D:/warp-mobile/apps/mobile_android/app/build/outputs/apk/debug/app-debug.apk
```

Clear app logs, open a real hosted session link, then inspect Warp logs:

```powershell
C:/Users/77585/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -c
C:/Users/77585/AppData/Local/Android/Sdk/platform-tools/adb.exe shell "am start -W -a android.intent.action.VIEW -d 'https://app.warp.dev/session/<session-id>' dev.warp.mobile.debug"
C:/Users/77585/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -d -s WarpMobile
```

## Expected Evidence

- `mobile_auth_handoff_bridge_exposed` appears only with `origin=https://app.warp.dev`.
- `mobile_auth_handoff_script_installed` confirms the document-start bootstrap was registered.
- `mobile_auth_handoff_token_requested` with `token_present=true` confirms the native token bridge is reachable from the hosted Warp page.
- `mobile_webview_page_finished` for `host=app.warp.dev` confirms the embedded page completed loading.
- Reopening an already-open session link should emit `mobile_tab_create_deduplicated`.

## Screenshot Caveat

On the current MIUI test device, `screencap` can return an all-black AOD/keyguard image while `dumpsys activity` still reports `dev.warp.mobile.debug/dev.warp.mobile.MainActivity` as resumed. Check `dumpsys power` for `mWakefulness=Dozing` before treating a black screenshot as an app rendering failure. In that state, logcat and UIAutomator will show SystemUI/keyguard rather than Warp UI.
