# Module 06: Session Capability State

## 模块目标

Session Capability State 决定 Android 原生输入是否允许。它把 Warp shared-session 角色和连接状态映射到原生 UI 状态。

Android Shell 不能从 URL 推断执行权限，必须信任已加载 Warp session 页面发出的显式 capability event。

## 当前依据

`app/src/terminal/shared_session/mod.rs` 已有 shared session 状态和 `is_executor` 角色判断。移动端需要从 Web/WASM 暴露等价状态，而不是在 Android 侧重新推断。

## 输入

- WASM `SESSION_JOINED`
- WASM `TERMINAL_INPUT_CAPABILITY_CHANGED`
- WASM `SESSION_RECONNECTING`
- WASM `SESSION_RECONNECTED`
- WASM `SESSION_LEFT`
- WebView load 和 bridge state
- Android lifecycle resume/pause

## Native State

```kotlin
data class TerminalInputCapability(
  val sessionIdHash: String?,
  val role: SharedSessionRole?,
  val canExecute: Boolean,
  val reason: InputDisabledReason,
  val bridgeGeneration: Long,
)
```

角色：

- `unknown`
- `reader`
- `executor`
- `full`
- `sharer`

禁用原因：

- `bridge_not_ready`
- `session_not_joined`
- `shared_session_reader`
- `session_reconnecting`
- `webview_loading`
- `webview_failed`
- `renderer_crashed`
- `session_ended`
- `auth_required`

## 状态迁移

1. 选择新 URL -> capability 重置为 `bridge_not_ready`。
2. WebView committed -> capability 保持 disabled。
3. Bridge ready -> capability 仍 disabled，直到收到 session/capability event。
4. Session joined as reader -> disabled，reason 为 reader。
5. Session joined as executor/full -> enabled。
6. Reconnecting -> disabled 或 buffer-eligible，取决于产品策略。
7. Reconnected with same generation -> 等待新 capability 确认。
8. WebView reload 或 generation 变化 -> disabled，直到 fresh capability。
9. Renderer crash -> disabled。
10. Session closed -> disabled，并返回入口或结束页。

## 输入门控

发送任何 native key action 前必须满足：

1. Active WebView state ready。
2. Bridge generation 匹配。
3. Session id 匹配 active session。
4. Capability `canExecute=true`。
5. Keyboard action 未过期。

不满足时：

- 可解释禁用：不发送，并记录 reason。
- 短暂未 ready：按 buffering policy 处理。
- role 降级或 session 结束：立即清空 buffer 和 repeat press。

## UI 行为

- `canExecute=true`: 内置键盘正常可用。
- `reader/viewer-only`: 键盘按键禁用，但保留切换、复制、查看状态。
- `connecting/reconnecting`: 可显示键盘，但输入进入短缓冲或禁用，取决于 bridge 状态。
- `disconnected/ended`: 清空输入缓冲，禁用发送。
- `auth_required`: 显示登录或外部浏览器打开入口。

## 实现步骤

1. Web/WASM 在 join、role change、disconnect、reconnect、end 时发 capability event。
2. Android `SessionCapabilityStore` 保存最新状态。
3. Builtin keyboard 订阅 capability，而不是直接订阅 WebView 状态。
4. 状态变化时取消 repeat press，必要时清空 modifier one-shot。
5. bridge reject 输入时，将 reject reason 映射为 capability 或 transient error。

## 日志

- `mobile_session_capability_received`
- `mobile_session_input_enabled`
- `mobile_session_input_disabled`
- `mobile_session_role_changed`
- `mobile_session_reconnect_started`
- `mobile_session_ended`
- `mobile_session_buffer_cleared`

字段：

- `session_id_hash`
- `status`
- `role`
- `can_send_input`
- `disabled_reason`
- `previous_status`
- `bridge_generation`

## 测试

- 状态 reducer unit test。
- role 从 executor/full 变 reader 后，键盘立即禁用。
- disconnect 时 repeat 停止、buffer 清空。
- reconnect 后按最新 capability 恢复。
- bridge reject 不会导致无限重发。
- generation mismatch 必须拒绝输入。

## 退出标准

- Android 键盘不会在 reader、断开、结束、未授权状态发送输入。
- 每次输入禁用都有可解释 reason。
- 状态变化和键盘 UI 变化之间有日志关联。
