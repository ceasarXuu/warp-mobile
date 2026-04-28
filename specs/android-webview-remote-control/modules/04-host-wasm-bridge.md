# Module 04: Host/WASM Bridge

## 模块目标

建立 Android 原生层、WebView JavaScript 宿主层和 WASM/Rust 应用之间的双向事件协议。它是键盘、会话状态和诊断的边界。

## 当前依据

`crates/warp_web_event_bus/src/lib.rs` 已提供 WASM 到宿主页面的 `WarpEvent` 和 `warpEmitEvent`。Android 方案应保留这个方向，并补充 Android 到 Web/WASM 的命令入口。

## 职责

- 定义版本化 JSON message schema。
- Android -> Web/WASM：发送 terminal action、请求当前 session capability、通知宿主状态。
- Web/WASM -> Android：发送 session 状态、输入能力、ack/reject、登录和 native open 事件。
- 为每个消息分配 sequence id。
- 处理超时、重复、乱序和页面 reload。

## 消息格式

所有消息必须包含：

- `kind`
- `version`
- `sequenceId`
- `timestampMs`
- `source`

可选包含：

- `sessionIdHash`
- `payload`
- `error`

## Android 到 Web/WASM 动作

```json
{
  "kind": "TERMINAL_ACTION",
  "version": 1,
  "sequenceId": "android-keyboard-000001",
  "timestampMs": 1770000000000,
  "source": "android_builtin_keyboard",
  "payload": {
    "type": "sendNavigation",
    "key": "ArrowUp"
  }
}
```

### 支持的 action

- `sendPrintable`: `{ "text": "a" }`
- `sendRaw`: `{ "code": "Enter" }`
- `sendModifiedKey`: `{ "key": "c", "modifiers": ["ctrl"] }`
- `sendNavigation`: `{ "key": "ArrowUp" }`

## Web/WASM 到 Android 事件

- `SESSION_JOINED`
- `SESSION_INPUT_CAPABILITY_CHANGED`
- `SESSION_DISCONNECTED`
- `SESSION_ENDED`
- `AUTH_REQUIRED`
- `OPEN_ON_NATIVE_REQUESTED`
- `TERMINAL_ACTION_ACK`
- `TERMINAL_ACTION_REJECTED`
- `BRIDGE_READY`
- `WASM_READY`

## 错误处理

- Android 发送消息后启动短超时。
- 页面未 ready 时，键盘动作进入有上限缓冲。
- 收到 reject 时，键盘状态不重试无限循环，只记录并按 session capability 更新 UI。
- WebView reload 后 sequence id 继续递增，但 bridge session id 更新。
- schema 校验失败直接拒绝，不尝试容错执行。

## 实现步骤

1. 在 Web 宿主层定义 `window.warpAndroidBridge.receiveFromAndroid(message)`。
2. WASM 侧增加 host command receiver，把 terminal action 转入现有 terminal input pipeline。
3. 复用 `warpEmitEvent` 或扩展 event bus，把 session capability 发给 Android。
4. Android 侧建立 `BridgeClient`，封装 `evaluateJavascript`、ack tracking 和 ready state。
5. 建立 JSON schema fixture，Android、JS、Rust 测试共用。
6. 增加 dev-only bridge inspector 页面或日志开关。

## 日志

- `mobile_bridge_message_sent`
- `mobile_bridge_message_received`
- `mobile_bridge_message_ack`
- `mobile_bridge_message_rejected`
- `mobile_bridge_message_timeout`
- `mobile_bridge_schema_invalid`
- `mobile_bridge_ready_state_changed`

字段：

- `bridge_session_id`
- `sequence_id`
- `kind`
- `source`
- `session_id_hash`
- `payload_size`
- `error_code`

输入内容只允许记录 `payload_hex` 或动作摘要，不记录明文命令。

## 测试

- Android unit test：message encode/decode、ack timeout、schema reject。
- JS test：`receiveFromAndroid` 校验和转发。
- Rust/WASM test：terminal action 到 input pipeline。
- Instrumentation：fake page 回传 ack/reject。
- 真实设备冒烟：内置键盘按键产生 sequence id，并在 Web/WASM 收到 ack。

## 退出标准

- 每条键盘输入都能通过 sequence id 追踪到 ack 或 reject。
- 页面 reload 后不会把旧 bridge 消息发送到新页面。
- schema 变化必须通过版本号显式处理。
