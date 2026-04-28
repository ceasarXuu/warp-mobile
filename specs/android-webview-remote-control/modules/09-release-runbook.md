# Module 09: Release Runbook

## 模块目标

记录 Android WebView 远控壳的构建、安装、启动、日志和发布前冒烟流程。脚本、module 路径和 package name 稳定后必须同步更新本文件。

## 前置条件

- Android Studio 已安装。
- Android SDK 已安装。
- JDK 17+ 可用。
- Android 设备已开启 USB debugging。
- Warp desktop 或已安装 app 可以启动 shared session。
- 可访问 WebView 使用的 Warp server origin。

## 环境检查

当前 Android module 命令：

```powershell
cd D:\warp-mobile
.\apps\mobile_android\gradle.ps1 :app:tasks
adb devices
```

`apps\mobile_android\gradle.ps1` 会在本机缺少 Gradle 时下载 Gradle 8.10.2 到 `%LOCALAPPDATA%\WarpMobile\gradle`，并在 `JAVA_HOME` 未设置时使用 Android Studio 的 `D:\Android Studio\jbr`。

## Debug Build

当前命令：

```powershell
cd D:\warp-mobile
.\apps\mobile_android\gradle.ps1 :app:testDebugUnitTest :app:assembleDebug
```

预期产物：

```text
apps/mobile_android/app/build/outputs/apk/debug/app-debug.apk
```

## Install

```powershell
adb install -r -t -g apps\mobile_android\app\build\outputs\apk\debug\app-debug.apk
```

如果因为旧包签名不一致安装失败，只卸载该 debug package：

```powershell
adb uninstall dev.warp.mobile.debug
```

只能卸载明确 app package，不能做宽泛设备清理。

如果小米设备返回 `INSTALL_FAILED_USER_RESTRICTED`，先点亮并解锁设备，在手机上确认 USB 安装授权，然后重试同一个 install 命令。

## 使用 App Link 启动

```powershell
adb shell am start `
  -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity `
  -a android.intent.action.VIEW `
  -d "https://app.warp.dev/session/00000000-0000-0000-0000-000000000000?pwd=secret"
```

替换 host 和 session id 为当前环境。不要把真实 secret 粘贴进提交文档或 issue comment。

本地 fake WebView smoke 使用：

```powershell
adb shell am start `
  -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity `
  -a android.intent.action.VIEW `
  -d "https://debug.warp.local/session/00000000-0000-0000-0000-000000000000"
```

真实链接 smoke 使用用户提供或 staging 生成的真实 `https://app.warp.dev/session/{uuid}`：

```powershell
adb logcat -c
adb shell am start `
  -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity `
  -a android.intent.action.VIEW `
  -d "https://app.warp.dev/session/{uuid}"
adb logcat -d -s WarpMobile
```

不要把真实 UUID、token、cookie 或完整 URL 写入提交文档；提交文档只保留 `{uuid}` 或 `session_id_hash`。

## Logcat Filters

```powershell
adb logcat -c
adb logcat WarpMobile:D WarpMobileWebView:D WarpMobileKeyboard:D *:S
```

如果 logger 统一使用单 tag JSON：

```powershell
adb logcat -s WarpMobile
```

当前 fake-page smoke 的预期事件顺序：

1. `mobile_shell_created`
2. `mobile_link_parse_succeeded`
3. `mobile_webview_load_started`
4. `mobile_bridge_message_received` with `BRIDGE_READY`
5. `mobile_bridge_message_received` with `SESSION_INPUT_CAPABILITY_CHANGED`
6. `mobile_keyboard_action_dispatched`
7. `mobile_bridge_message_sent`
8. `mobile_bridge_message_received` with fake ACK

当前真机快速键盘 smoke：

```powershell
adb logcat -c
adb shell am start -n dev.warp.mobile.debug/dev.warp.mobile.MainActivity -a android.intent.action.VIEW -d "https://debug.warp.local/session/00000000-0000-0000-0000-000000000000"
adb shell input tap 1035 2646
adb shell input tap 1000 1960
adb shell input tap 735 2070
adb logcat -d -s WarpMobile
```

坐标基于当前测试设备 `1220x2712`。更换设备后先执行 `adb shell uiautomator dump /sdcard/warp-mobile-ui.xml` 检查按键 bounds，再更新临时 smoke 坐标；不要把坐标写死进自动化测试。

真实链接 smoke 的额外判断：

- `mobile_webview_load_started` 只能证明 SPA 主页面开始加载，不代表 session 可控。
- 如果出现 `mobile_webview_http_error` 且 `path=/auth/session`、`path=/sessions/{uuid}`、`status=401`，当前失败点是 Warp web auth/session API，不是 native link parser。
- 如果出现 `mobile_webview_external_url_blocked`，先判断该 host 是否属于必要的 Warp/auth origin；只添加明确 origin，不开放通配外链。
- 只有看到生产 Web 侧 bridge ready/capability 事件后，才能继续测内置键盘真实输入。

## 发布前冒烟

1. 清理旧 logcat。
2. 安装 debug 或 release candidate APK。
3. 用 App Link 打开 staging session。
4. 验证 WebView 只加载 allowlist origin。
5. 验证 bridge ready 和 session capability。
6. 用内置键盘输入 harmless command。
7. 验证 Ctrl+C、方向键、Backspace、Enter。
8. 切换系统输入法并返回内置键盘。
9. App 后台恢复、旋转屏幕。
10. 导出诊断包。
11. 确认日志和诊断包无完整 URL、token、cookie、用户输入明文。

## 常见失败处理

### `adb devices` 无设备

- 检查 USB debugging。
- 重新插拔设备。
- 执行 `adb kill-server` 和 `adb start-server`。
- 记录设备型号、Android 版本和 adb 输出。

### 安装失败

- 如果是 signature mismatch，只卸载明确 debug package。
- 如果是 storage 或 ABI 问题，记录完整 `adb install` 输出。
- 不执行全局清理或恢复出厂操作。

### App Link 没有进入应用

- 检查 manifest intent filter。
- 检查 host 是否是当前 build allowlist。
- 用 `adb shell am start` 直接启动，区分系统关联问题和 app 内解析问题。

### WebView 空白

- 查 WebView load 日志。
- 检查 SSL、HTTP status、renderer crash。
- Debug build 打开 WebView remote debugging 后用 Chrome inspect 检查 console。

### 键盘无输入

- 查 keyboard pressed event。
- 查 bridge message sequence id。
- 查 session capability 是否 disabled。
- 查 WASM ack/reject。

## 文档维护规则

- 每次新增真实命令、打包路径、日志 tag、签名方式或调试技巧，都更新本文件。
- 每次出现真实设备问题，补充失败处理路径。
- 每次发布前冒烟流程变化，同步更新 `08-testing-validation.md`。

## 退出标准

- 构建、安装、启动、日志、冒烟和诊断都有固定操作入口。
- 新成员可以按本文档完成一次移动远控发布前检查。
- 失败时有明确日志路径，而不是从头试错。
