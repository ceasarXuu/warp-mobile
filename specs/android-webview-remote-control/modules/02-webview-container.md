# Module 02: WebView Container

## 模块目标

WebView Container 负责加载 Warp shared-session Web 页面，并只暴露移动远控所需的最小原生集成能力。它是最敏感的 Android 模块，因为它运行 Web 内容并提供 JavaScript bridge。

## 负责范围

1. 配置 WebView settings。
2. 加载已校验的 shared-session URL。
3. 强制执行 origin 和 scheme allowlist。
4. 安装和移除 JavaScript bridge object。
5. 上报 load、navigation、SSL、HTTP、console、render-process 事件。
6. 提供 fake-page mode，用于 instrumentation tests。

## WebView 设置

默认策略：

- JavaScript enabled。
- DOM storage enabled。
- File access disabled。
- Content access disabled。
- Mixed content blocked。
- Universal access from file URLs disabled。
- Media playback without gesture disabled，除非产品需求变化。
- WebView debugging 只在 debug build 开启。

Cookie 策略必须显式。如果认证需要 cookie，只允许 Warp web origin 所需 cookie，不在未验证前打开广泛 third-party cookie。

## 导航策略

允许：

- 配置的 Warp server root origin。
- 认证或 handoff 必需的配置 origin。
- served WASM bundle 必需的静态资源 origin。

阻断：

- `file://`
- `content://`
- 未显式映射为安全外部动作的 `intent://`
- 未知 HTTPS origin。
- 非 debug build 中的 HTTP origin。

任何阻断都记录脱敏事件；如果阻断影响 session 使用，需要显示用户可理解的 blocked state。

## Bridge 安装

Bridge 方法必须窄：

- `emitWarpEvent(json: String)` 或等价 host event sink。
- 可选 readiness handshake。

禁止向 JavaScript 暴露通用 `eval`、文件、剪贴板、shell 或 settings mutation 方法。

Bridge 只有在以下条件同时满足时才视为有效：

1. 顶层 WebView URL origin 在 allowlist 内。
2. 当前 bridge generation 匹配 active session。
3. 页面已发出 ready 或 joined event。

## 加载状态

- `NotCreated`
- `Creating`
- `Loading`
- `DomReady`
- `BridgeReady`
- `WasmReady`
- `Failed`
- `RendererGone`

`BridgeReady` 只能说明 JS 通道可用，不能说明 session 已加入。输入启用必须等待 session capability。

## 实现步骤

1. 建立 `RemoteSessionWebView` wrapper，隔离 WebView 配置。
2. 建立 `RemoteSessionWebViewClient` 和 `RemoteSessionWebChromeClient`。
3. 接入 allowlist 和外链转发策略。
4. JS bridge 注册和释放跟随页面生命周期。
5. 暴露 `WebViewLoadState` 给 Android Shell。
6. 增加 renderer crash 恢复路径：销毁旧 WebView，重新创建并加载同一 `SessionLaunchRequest`。
7. 增加 fake page 测试模式，用于本地验证 bridge、capability 和键盘输入。

## 日志

- `mobile_webview_created`
- `mobile_webview_load_started`
- `mobile_webview_progress`
- `mobile_webview_page_finished`
- `mobile_webview_http_error`
- `mobile_webview_ssl_blocked`
- `mobile_webview_renderer_gone`
- `mobile_webview_external_url_blocked`
- `mobile_webview_destroyed`

字段：

- `session_id_hash`
- `url_hash`
- `host`
- `path_kind`
- `http_status`
- `is_main_frame`
- `renderer_priority`
- `failure_reason`

## 测试

- fake WebView page instrumentation，验证 JS bridge 注册时机。
- allowlist 单元测试。
- HTTP error 和 renderer crash 使用测试页或 mock client 覆盖。
- viewport/inset 手工冒烟：内置键盘、系统输入法、横屏。
- unknown navigation blocked。
- reload 后 bridge generation 更新，旧消息不能继续生效。

## 退出标准

- WebView 不加载非 allowlist 页面。
- 加载失败可以恢复或明确阻断。
- Bridge 生命周期不会跨页面残留。
- WebView 日志能定位页面加载阶段。
