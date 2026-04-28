# Module 08: Testing and Validation

## 模块目标

移动远控跨 Android 原生、WebView、JavaScript/WASM 和现有 Warp shared-session 代码。验证必须覆盖每个边界，不能只依赖一次手工演示。

## 测试分层

### Kotlin Unit Tests

目标：

- `SharedSessionLinkParser`
- `Redaction`
- `RecentSessionStore` serializer
- `TerminalActionDispatcher`
- `TerminalModifierController`
- `TerminalRepeatPressController`
- `TerminalKeyboardModeController`
- input gate and buffering policy

必测场景：

- 合法 `/session/{uuid}` URL。
- 无效 UUID。
- unsupported origin。
- 敏感 query 脱敏。
- Ctrl/Alt/Shift payload mapping。
- one-shot 和 locked modifier lifecycle。
- fake scheduler 下的 navigation repeat timing。
- reader role 拒绝输入。
- executor/full role 允许输入。
- bridge generation mismatch 拒绝输入。

### Compose UI Tests

目标：

- Session entry screen。
- Built-in keyboard。
- More sheet。
- Disabled keyboard state。
- Diagnostics export affordance。

必测场景：

- 点击 `q` 发送 printable action。
- Ctrl then `c` 发送 Ctrl+C action。
- Shift then `1` 发送 `!`。
- Backspace 发送 Backspace action。
- Reader state 禁用 action buttons。
- More sheet 发送 Delete 和 PageUp。
- 组件使用 `WarpMobileTheme`，不出现硬编码 hex color。
- Primary、Secondary、Naked、Disabled、Danger 状态和 token fixture 匹配。

### Design Parity Tests

目标：

- Android Compose component catalog。
- 未来 iOS SwiftUI/UIKit component catalog。
- Web/Desktop token fixture。

必测场景：

- 同一 token fixture 下，Android/iOS 的 button、dialog、tooltip、keyboard key、inline banner 视觉状态一致。
- dark/light/custom theme 下 token 名称不缺失。
- 字体放大、横屏、窄屏下文本不溢出。
- native 组件不得使用未登记 token 或 feature-specific 颜色。

### Android Instrumentation Tests

使用 fake local web page 或 test asset。

必测场景：

- App Link launch 加载 fake session。
- Fake page 发出 `SESSION_JOINED`。
- Fake page 发出 Reader -> Executor capability changes。
- Native keyboard 发送 JSON 到 fake page。
- Fake page reload 改变 bridge generation。
- Unknown navigation 被阻断。
- Load failure 显示 native error。
- Background/resume 后需要 readiness 才能输入。

### Web/WASM Tests

目标：

- host command receiver validates schema。
- terminal action enters existing input pipeline。
- invalid action is rejected with structured reason。
- session capability event is emitted on join and role change。
- Android bridge command 不绕过现有 terminal input pipeline。

### Real Device Smoke

1. 安装 debug build。
2. 从 Android intent 打开真实或 staging session link。
3. 等待 WebView load 和 bridge ready。
4. 验证 session status 和 input capability。
5. 输入 `pwd` 或等价 harmless command。
6. 按 ArrowUp 和 Enter。
7. 长命令运行期间按 Ctrl+C。
8. 在 prompt 中长按 Backspace。
9. 切换到系统输入法，输入中文或长文本，再切回内置键盘。
10. App 后台再恢复。
11. 旋转屏幕。
12. 采集日志，并确认没有 secret 泄漏。

## 推荐命令占位

具体 Gradle module 名称在 Android scaffold 落地后固定。当前文档先规定命令形态：

```powershell
.\gradlew :mobile:android:app:testDebugUnitTest
.\gradlew :mobile:android:app:connectedDebugAndroidTest
.\gradlew :mobile:android:app:assembleDebug
adb install -r .\mobile\android\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -a android.intent.action.VIEW -d "https://app.warp.dev/session/<session-id>"
adb logcat -s WarpMobileRemote WarpBridge WarpKeyboard WebView
```

如果最终 module 路径不同，第一次 scaffold 合并时必须更新本文件和 release runbook。

## 冒烟检查清单

- 分享链接可冷启动打开。
- WebView 只加载 allowlist URL。
- 页面失败时出现可恢复错误页。
- bridge ready 后能请求当前 capability。
- executor/full role 下键盘可输入。
- reader/viewer-only 下键盘不能输入。
- Ctrl+C、Tab、Esc、方向键、Backspace、Enter 正常。
- 长按重复可取消。
- 切换系统输入法后焦点返回终端。
- 后台恢复后 bridge 状态正确。
- 日志没有完整 URL、token、cookie、用户输入明文。
- native 组件截图和 Warp web/desktop 组件语义一致，没有平台各自新增的视觉变体。

## 失败定位路径

### 链接打不开

1. 查 `mobile_link_open_received` 是否存在。
2. 查 `mobile_link_parse_failed` 的 reason。
3. 查 allowlist 是否拒绝 host。
4. 查 WebView 是否收到 load URL。

### 页面加载失败

1. 查 `mobile_webview_load_started`。
2. 查 HTTP/SSL/renderer 日志。
3. 用外部浏览器打开同一脱敏后的 host/path 验证服务端状态。
4. 若 renderer crash，重新创建 WebView 后再试。

### 键盘输入丢失

1. 查 `mobile_keyboard_key_pressed`。
2. 查 `mobile_keyboard_action_dispatched` 的 sequence id。
3. 查 `mobile_bridge_message_sent`。
4. 查 WASM 是否返回 ack/reject。
5. 查 `mobile_session_input_disabled` 是否在同一时间发生。

### 键盘遮挡终端

1. 记录 Android window inset。
2. 记录 Web visual viewport height。
3. 判断是否 Android 和 Web 都做了重复补偿。
4. 横竖屏分别截图留档。

## 退出标准

- 每个模块至少有 unit 或 instrumentation 覆盖入口。
- 真实设备冒烟步骤可以由新成员按文档执行。
- 测试失败能定位到具体模块，而不是只看到“移动端不可用”。
- 视觉回归能定位到 token、组件映射或平台实现，不靠人工感觉判断。
