# Module 05: Builtin Keyboard

## 模块目标

在 Android 原生层实现终端友好的内置键盘，行为设计复用 Astropath 的内置键盘体系。键盘服务于远控终端，不是普通文本输入框。

## Astropath 复用范围

复用行为合同：

- `systemIme` / `builtin` 双模式。
- `leftPeek` / `center` / `rightPeek` 三锚点。
- 左侧补充区、中心主键盘、右侧导航和符号区。
- `ctrl`、`alt`、`shift` 三个修饰键。
- inactive、one-shot、locked 三态修饰键状态机。
- 长按重复控制器。
- 触觉和按键音反馈。
- More sheet 提供导航键、F1-F12、控制键和特殊字符。
- 输入 ready 前短缓冲，失败后有日志。

不直接复用内容：

- 不复制 Flutter widget 代码。
- 不复制 Astropath 的项目命名、主题或平台 channel。
- 不把键盘耦合到 Astropath remote session controller。

## 键盘模式

### Builtin

默认模式。显示三段终端键盘，通过 bridge 发送 terminal action。

### System IME

显示系统输入法和 accessory bar。用于中文、长文本和系统输入习惯。Accessory bar 保留 Esc、Tab、Ctrl、Alt、方向、More 等终端入口。

## 三段布局

### Left Peek

用于低频但终端常用的扩展键：

- 启动或面板类按钮：More、Paste、Keyboard Mode。
- Tab、`=`、`:`、`_`、`$`。
- 括号和反斜杠。

### Center

主输入区：

- Esc、常用符号行：`-`、`/`、`.`、`~`、`|`、`+`。
- Backspace。
- 数字行。
- QWERTY 字母区。
- Shift、Ctrl、Alt、Space、Enter。

### Right Peek

导航和 shell 符号：

- ArrowUp、ArrowDown、ArrowLeft、ArrowRight。
- Home、End、PageUp、PageDown。
- Delete。
- `>`, `<`, `&`, `;`, `*`, `?`, quote。

## 状态机

### ModifierState

```kotlin
enum class ModifierState {
    Inactive,
    OneShot,
    Locked
}
```

点击规则：

- `Inactive -> OneShot`
- `OneShot -> Locked`
- `Locked -> Inactive`

消费规则：

- printable、navigation、raw key 发送后，one-shot 修饰键自动回到 inactive。
- locked 修饰键不自动消费。
- 切换键盘模式时保留 locked，清除 one-shot。

### AnchorState

```kotlin
enum class KeyboardBandAnchor {
    LeftPeek,
    Center,
    RightPeek
}
```

拖拽规则：

- 横向拖拽超过阈值才切换锚点。
- 松手后吸附到最近锚点。
- 锚点变化写日志。

## Action Dispatcher

键盘 UI 只产生 `KeyboardIntent`，由 dispatcher 转换为 bridge terminal action。

```kotlin
sealed interface KeyboardIntent {
    data class Printable(val text: String) : KeyboardIntent
    data class Raw(val code: RawKeyCode) : KeyboardIntent
    data class Navigation(val key: NavigationKey) : KeyboardIntent
    data class ToggleModifier(val key: ModifierKey) : KeyboardIntent
    data object SwitchToSystemIme : KeyboardIntent
    data object OpenMoreSheet : KeyboardIntent
}
```

Dispatcher 输出：

- printable + shift -> 大写或符号映射。
- ctrl + letter -> control action。
- alt + key -> Alt modified action，WASM 侧决定是否编码为 ESC prefix。
- raw key -> `sendRaw`。
- navigation -> `sendNavigation`。

## Repeat Press

适用按键：

- Backspace
- Delete
- ArrowUp
- ArrowDown
- ArrowLeft
- ArrowRight
- PageUp
- PageDown

默认参数沿用 Astropath 设计：

- 初始延迟约 500ms。
- 重复间隔约 80ms。

参数应集中配置，便于不同设备调优。

## Feedback

- 支持触觉反馈偏好。
- 支持按键音偏好。
- 长按重复不应每 80ms 都触发强反馈，避免干扰。
- 反馈失败不影响输入发送。

## 输入缓冲

当 bridge ready 但 session capability 未 ready 时，允许短缓冲：

- 每个 session 最多保留固定数量动作。
- 超过上限丢弃最旧或拒绝新输入，策略必须写日志。
- session 变更、WebView reload、role 变为不可输入时清空缓冲。
- 控制类输入如 Ctrl+C 可进入缓冲，但必须有更短 TTL。

## 日志

- `mobile_keyboard_mode_changed`
- `mobile_keyboard_anchor_changed`
- `mobile_keyboard_key_pressed`
- `mobile_keyboard_modifier_changed`
- `mobile_keyboard_repeat_started`
- `mobile_keyboard_repeat_stopped`
- `mobile_keyboard_action_dispatched`
- `mobile_keyboard_action_buffered`
- `mobile_keyboard_action_dropped`
- `mobile_keyboard_feedback_failed`

字段：

- `keyboard_mode`
- `anchor`
- `key_code`
- `action_type`
- `modifier_state`
- `sequence_id`
- `session_id_hash`
- `buffer_size`

禁止记录用户输入明文；printable 只记录类别、长度和可选 hex。

## 测试

- Modifier controller unit test：三态切换、one-shot 消费、locked 保留。
- Action dispatcher unit test：Esc、Tab、Enter、Backspace、Delete、方向键、Ctrl+C、Ctrl+D、Ctrl+Z、Alt 前缀。
- Layout snapshot 或 Storybook 等价预览：三锚点、横竖屏、字体缩放。
- Repeat controller test：延迟、间隔、取消。
- Instrumentation：点击键盘按键后 fake bridge 收到正确 JSON。
- 真实设备冒烟：shell 历史导航、Ctrl+C 中断、长按删除、切换系统输入法后返回。

## 退出标准

- 键盘所有动作只通过 dispatcher 和 bridge 发送。
- 输入权限变化会立即反映到键盘可用状态。
- 键盘不会遮挡终端最后一行。
- 常用终端操作在真实 Android 设备上可完成。
