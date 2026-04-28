# Module 01: Android Shell

## 模块目标

Android Shell 是 Warp Web 远控移动版的原生应用边界。它负责 Android 生命周期、App Links、会话入口、WebView 承载、键盘布局、错误恢复、诊断导出和设置入口。

它必须保持薄壳定位：不重写 Warp session 渲染、不解析终端协议、不实现第二套 shared-session 协议。

## 负责范围

1. 创建并初始化 Android app process。
2. 接收 Android intents，并把 session URL 交给链接路由模块。
3. 渲染入口、加载、远控、错误、诊断状态。
4. 管理 WebView 生命周期和状态组合。
5. 管理内置键盘生命周期、底部停靠和系统输入法切换。
6. 保存原生设置和最近会话的脱敏元数据。
7. 导出脱敏诊断信息。

## 建议包边界

```text
apps/mobile_android/
  app/
    src/main/
      AndroidManifest.xml
      java/dev/warp/mobile/
        MainActivity.kt
        shell/
          WarpMobileApp.kt
          WarpMobileState.kt
          SessionEntryScreen.kt
          RemoteControlScreen.kt
          ErrorScreen.kt
        session/
          SharedSessionLinkParser.kt
          RedactedSessionUrl.kt
          RecentSessionStore.kt
        webview/
          WarpSessionWebView.kt
          WarpWebViewState.kt
          WarpWebBridge.kt
        keyboard/
          TerminalBuiltinKeyboard.kt
          TerminalActionDispatcher.kt
          TerminalModifierController.kt
          TerminalRepeatPressController.kt
          TerminalKeyboardModeController.kt
          TerminalKeyboardFeedbackDispatcher.kt
        observability/
          MobileEventLogger.kt
          Redaction.kt
          DiagnosticsExporter.kt
```

具体目录可以随现有 Android scaffold 调整，但职责边界不应漂移。

## 顶层状态模型

- `NoSession`: 没有有效 session URL。
- `ParsingLink`: 正在解析传入链接。
- `LoadingSession`: 有效 URL 正在 WebView 加载。
- `SessionReady`: WebView ready，页面已加入 session。
- `SessionError`: 可恢复加载或 bridge 错误。
- `RendererCrashed`: WebView renderer 崩溃或被系统杀死。
- `ClosingSession`: 用户请求返回入口或退出会话。

WebView callback 不应直接随意修改 Compose 状态。事件应进入 reducer 或 view model，让测试可以在没有真实 WebView 的情况下覆盖状态迁移。

## 生命周期规则

1. `onCreate` 和 `onNewIntent` 使用同一个 link parser。
2. 新 intent 可以替换当前 session，但必须先完成 URL 校验。
3. `onPause` 记录 bridge generation，并暂停瞬时输入能力。
4. `onResume` 请求 WebView bridge 重新报告 readiness，再恢复输入。
5. `onDestroy` 清理 WebView 引用、取消 repeat press、关闭 diagnostics session。

## UI 组合

远控屏幕按以下层级组织：

```text
RemoteControlScreen
  WebView area
  Session status overlay
  Bottom keyboard host
    Builtin keyboard
    System IME accessory bar
  Error or diagnostics sheet
```

布局规则：

- WebView 占据主区域，键盘停靠底部。
- 内置键盘出现时，终端内容区必须获得等价 inset，不能遮挡最后一行。
- 系统输入法出现时，使用 Android window inset，不硬编码高度。
- 横屏下限制键盘高度，优先保留终端可视行数。
- 状态栏和导航栏可 edge-to-edge，但交互元素必须留安全边距。

## Back 行为

处理顺序：

1. 关闭 More sheet 或诊断 sheet。
2. 关闭系统输入法。
3. 如果 WebView 能后退，执行 WebView back。
4. 如果会话 active，显示退出确认或回到入口。
5. 否则退出 Activity。

## 日志

- `mobile_shell_created`
- `mobile_shell_new_intent`
- `mobile_shell_state_changed`
- `mobile_shell_back_pressed`
- `mobile_shell_recover_action_clicked`
- `mobile_shell_destroyed`

字段：

- `state_from`
- `state_to`
- `session_id_hash`
- `orientation`
- `keyboard_mode`
- `webview_ready`

## 测试

- Activity cold start with valid session link。
- `onNewIntent` 替换旧会话。
- 横竖屏旋转后保持 session request。
- Back 处理顺序测试。
- 错误页动作测试：重试、复制、外部浏览器打开。
- 生命周期 resume 后未收到 bridge ready 前不能发送输入。

## 退出标准

- 不同入口打开链接行为一致。
- 生命周期变化不丢 session request。
- 错误恢复路径可执行且有日志。
- Shell 代码不包含终端协议或 shared-session 协议实现。
