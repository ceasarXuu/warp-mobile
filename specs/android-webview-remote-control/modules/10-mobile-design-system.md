# Module 10: Mobile Design System

## 模块目标

为 Android 和未来 iOS native shell 建立同一套移动视觉系统。目标不是重新设计移动版 Warp，而是把 Warp web/desktop 已有主题、组件语义和终端视觉语言映射到 native 平台。

这份文档是防分叉约束：Android Compose 和 iOS SwiftUI/UIKit 可以有平台实现差异，但不能有视觉语言差异。

## 已检查的 Warp UI 来源

### Theme Tokens

来源：

- `crates/warp_core/src/ui/theme/mod.rs`
- `crates/warp_core/src/ui/theme/color.rs`
- `app/src/themes/default_themes.rs`

关键 token：

- `background()`: 终端和大背景。
- `foreground()`: 默认前景。
- `accent()`: 主行动和强调。
- `surface_1()`, `surface_2()`, `surface_3()`: 递进 UI surface。
- `outline()`: 边框和分隔。
- `main_text_color()`, `sub_text_color()`, `hint_text_color()`, `disabled_text_color()`: 文本层级。
- `active_ui_text_color()`, `nonactive_ui_text_color()`, `disabled_ui_text_color()`: 常用 UI 文本。
- `ui_error_color()`, `ui_warning_color()`, `ui_green_color()`, `ui_yellow_color()`: 语义状态。
- `terminal_colors()`: 终端 ANSI palette。

### Buttons

来源：

- `app/src/view_components/action_button.rs`
- `crates/ui_components/src/button/themes.rs`

已有语义：

- `Primary`: accent fill，主行动。
- `Secondary`: 无 fill，有边框，hover 使用 foreground overlay。
- `Naked`: 无默认 fill/border，hover 使用 foreground overlay。
- `Disabled`: disabled fill/text。
- `DangerPrimary`, `DangerSecondary`, `DangerNaked`: destructive action。
- `PaneHeader`: muted header action。
- `PrimaryRightBiased`: gradient accent 的 split/adjoined 场景。

移动端不得新增 `RemoteControlPrimaryButton`、`KeyboardSubmitTheme` 这类 feature-specific 主题。若已有语义不够，先扩展本模块文档，再评估是否需要改设计系统本身。

### Dialogs, Tooltips, Shortcuts

来源：

- `crates/ui_components/src/dialog.rs`
- `app/src/ui_components/dialog.rs`
- `crates/ui_components/src/tooltip.rs`
- `crates/ui_components/src/keyboard_shortcut.rs`

可复用规则：

- Dialog 使用 `surface_1`、1px border、8px radius、header semibold、footer top border。
- Tooltip 使用 tooltip background、`surface_2` border、4px radius、紧凑 padding、比 UI font 小 2px。
- KeyboardShortcut 使用 UI font -1、4px padding、3px radius，按键 label 优先复用 Warp key naming。

### Shared Session And Terminal UI

来源：

- `app/src/workspace/view/wasm_view.rs`
- `app/src/terminal/view/inline_banner/shared_sessions.rs`
- `app/src/terminal/view/shared_session/conversation_ended_tombstone_view.rs`

可复用规则：

- Web/WASM 远控主体直接复用，不在 native 重画 terminal renderer。
- Open-in-native / Open-in-Warp 类 CTA 使用 `Primary`。
- 次级跳转使用 `Secondary`。
- info/icon action 使用 `Naked`。
- remote-control active 状态可以使用 shared-session banner 语义：active 用 terminal red 边界，inactive 用 surface/outline。
- error tombstone 使用 error ANSI overlay 和 red border，而不是平台默认红色。

## 跨端实现策略

```text
Shared source of truth:
  WarpTheme / default themes / component semantic mapping

Generated artifacts:
  warp-mobile-tokens.json
  warp-mobile-components.json
  bridge and keyboard action schemas

Android:
  WarpMobileTheme
  WarpButton
  WarpDialog
  WarpTooltip
  WarpKeyboardShortcut
  WarpKeyboardKey
  WarpStatusBanner

iOS:
  WarpMobileTheme
  WarpButton
  WarpDialog
  WarpTooltip
  WarpKeyboardShortcut
  WarpKeyboardKey
  WarpStatusBanner
```

Android 和 iOS 的组件代码可以不同，但 token 名称、组件 variants、状态集合和截图用例必须一致。

## 复用优先级

1. 直接复用现有 Web/WASM 远控页面和 terminal renderer。
2. 直接复用现有 Warp theme 作为 token source of truth。
3. 直接复用现有 icon 语义和组件 variant 名称。
4. 用生成物复用 schema：token JSON、component JSON、bridge JSON schema、keyboard action schema。
5. 平台 native 只实现 renderer adapter：Compose 或 SwiftUI/UIKit 组件负责把同名 token 和同名 variant 画出来。

除非能证明 WarpUI Rust component 可以稳定嵌入移动 native 层，否则不把 Rust UI widget 直接嵌进 Android/iOS。这里的直接复用重点是远控核心、token、语义和 schema，而不是牺牲 WebView/IME/安全边界的原生控制权。

## Token 输出要求

移动端应从现有 Warp theme 导出 token fixture，而不是手写 palette。

建议 JSON：

```json
{
  "themeName": "warp-default-dark",
  "colors": {
    "background": "#000000ff",
    "foreground": "#ffffffff",
    "accent": "#...",
    "surface1": "#...",
    "surface2": "#...",
    "surface3": "#...",
    "outline": "#...",
    "textMain": "#...",
    "textSub": "#...",
    "textHint": "#...",
    "textDisabled": "#...",
    "error": "#...",
    "warning": "#..."
  },
  "terminalColors": {
    "normal": {},
    "bright": {}
  }
}
```

如果 `accent()` 或 `background()` 是 gradient，移动端必须支持等价表达；无法支持时只能使用文档化的 bias 规则，例如 `PrimaryRightBiased` 的右侧颜色策略。

## 组件映射

| 移动组件 | Warp 来源语义 | Android 实现 | iOS 实现 |
|---|---|---|---|
| Primary button | `PrimaryTheme` / `button::themes::Primary` | Compose `WarpButton.Primary` | SwiftUI `WarpButton(.primary)` |
| Secondary button | `SecondaryTheme` | Compose `WarpButton.Secondary` | SwiftUI `WarpButton(.secondary)` |
| Icon button | `NakedTheme` + icon | Compose `WarpIconButton` | SwiftUI `WarpIconButton` |
| Danger action | `DangerPrimary/Secondary/Naked` | Compose danger variants | SwiftUI danger variants |
| Dialog | shared Dialog style | Compose `WarpDialog` | SwiftUI/UIKit `WarpDialog` |
| Tooltip | shared Tooltip style | Compose popup/tooltip | SwiftUI overlay/UIKit popover |
| Shortcut key | `KeyboardShortcut` | Compose `WarpShortcutKey` | SwiftUI `WarpShortcutKey` |
| Session banner | shared-session inline banner | Compose `WarpSessionBanner` | SwiftUI `WarpSessionBanner` |
| Keyboard key | button + keyboard shortcut semantics | Compose `WarpKeyboardKey` | SwiftUI/UIKit `WarpKeyboardKey` |

## Native Adaptation Rules

允许平台化：

- safe area / window insets。
- system back / swipe gesture。
- haptic feedback。
- Android IME 和 iOS keyboard safe area。
- platform logging。

不允许平台化：

- 自定义颜色 palette。
- 不同 button shape。
- 不同 disabled/error/active 状态表达。
- Android 和 iOS 各自定义 keyboard key variants。
- 为单个功能新建视觉主题。

## 内置键盘视觉约束

键盘行为复用 Astropath，但视觉归属 Warp：

- 普通 key 默认使用 Secondary 语义。
- 低优先级工具 key 使用 Naked/Icon 语义。
- active modifier 使用 accent outline 或 surface overlay。
- Enter 或主确认可用 Primary，但必须克制使用 accent 面积。
- disabled key 使用 Disabled 语义，不只设置 alpha。
- More sheet 使用 Dialog token，不做独立键盘风格弹层。
- 长按 pressed 状态复用 button pressed overlay。

## Component Catalog

实现前必须建立移动 component catalog。它承担 Storybook 等价职责：

- Android Compose catalog page。
- 未来 iOS catalog page。
- 每个组件覆盖 default、hover/pressed、active、disabled、danger、loading。
- 每个组件覆盖 dark、light、custom theme token fixture。
- 键盘覆盖 leftPeek、center、rightPeek、systemIme accessory、More sheet。

如果仓库后续引入 Web Storybook，移动 catalog 应复用同一 token fixture 和同名组件用例。

## Golden Tests

必需截图：

- Button variants。
- Dialog。
- Tooltip。
- KeyboardShortcut。
- Session status banner。
- Builtin keyboard 三锚点。
- Error/reconnecting/viewer-only 状态。

验收规则：

- Android 和 iOS 同名组件使用同名 token。
- 同名状态的视觉差异只能来自平台渲染字体差异或 safe area，不允许语义不同。
- 任何 golden 变化必须说明是 token 更新、组件规范更新还是平台实现 bug。

## 实现步骤

1. 增加 token export 脚本，从现有 `WarpTheme` 生成 mobile token fixture。
2. 增加 `warp-mobile-components.json`，声明组件 variant 和状态集合。
3. Android 建 `WarpMobileTheme` 和基础组件。
4. Android 建 component catalog 和 golden tests。
5. iOS 启动时复用相同 token/component fixture。
6. 每次移动 UI 变更先更新 catalog，再接入业务页面。

## 日志

- `mobile_design_tokens_loaded`
- `mobile_design_tokens_missing`
- `mobile_component_variant_rendered`
- `mobile_component_unregistered_token_used`
- `mobile_visual_golden_mismatch`

## 退出标准

- Android 远控 shell 和键盘不含硬编码颜色、圆角、字体层级。
- Android/iOS 共享同一份 token fixture 和组件 variant 名称。
- 新 native UI 能在 component catalog 中独立预览。
- 视觉验收可以指向 Warp web/desktop 组件语义，而不是主观描述。
