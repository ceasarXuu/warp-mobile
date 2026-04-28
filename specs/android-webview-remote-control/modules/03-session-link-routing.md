# Module 03: Session Link Routing

## 模块目标

Session Link Routing 把 Android intents、粘贴 URL 和最近会话选择转换为已校验的 WebView load request，并防止敏感 query 数据进入日志或存储。

## 当前依据

`app/src/uri/web_intent_parser.rs` 已处理 `/session/{session_id}`、UUID 校验、query 参数保留，以及把 Web session URL 重写为 `shared_session` native intent 的逻辑。Android 移动端应优先复用这套语义，避免另建一套链接解释器。

## 输入

- Android App Link intent。
- 手动输入 URL。
- 剪贴板粘贴。
- 最近会话条目。
- Debug fake-session URL。

## URL 要求

一个支持的 session URL 必须：

1. 使用允许的 scheme。
2. 匹配配置的 Warp server origin。
3. path 为 `/session/{session_id}`。
4. session id 是有效 UUID。
5. 保留 query 参数用于 WebView 加载。

无效 URL 返回 typed errors：

- `unsupported_scheme`
- `unsupported_origin`
- `missing_session_id`
- `invalid_session_id`
- `unsupported_path`
- `malformed_url`

## 输出

```kotlin
data class SessionLaunchRequest(
    val originalUrlHash: String,
    val loadUrl: String,
    val redactedUrl: String,
    val sessionId: String,
    val sessionIdHash: String,
    val preservedQueryKeys: Set<String>,
    val source: LaunchSource
)
```

## 脱敏规则

Parser 同时返回：

- `loadUrl`: WebView 实际加载的精确 URL。
- `redactedUrl`: 日志和诊断可用的安全 URL。

规则：

- 保留 scheme、host、path 和安全 query key name。
- 敏感 query value 替换为 `[REDACTED]`。
- `pwd`、`password`、`token`、`auth`、`key`、`code`、`state` 和未知高熵值一律视为敏感。
- 最近会话不存储敏感 query value。

## 最近会话

```kotlin
data class RecentSharedSession(
  val sessionIdHash: String,
  val redactedHost: String,
  val lastOpenedAtMillis: Long,
  val label: String?,
)
```

第一版不应存完整 load URL，除非另有加密存储设计。若重开最近会话缺少必需参数，可以要求用户重新粘贴原始链接。

## App Links

Manifest intent filters 应指向配置的生产 origin。Debug build 可通过 debug-only config 支持 staging 或 localhost-like origins。

App Link 处理不能为每个链接创建新 app instance。应使用 `singleTop` 或显式 intent routing，让 `onNewIntent` 能干净替换 active session。

## 实现步骤

1. 建立 Android 侧 `SharedSessionLinkParser`，行为对齐 Rust 侧 `parse_web_intent_from_url`。
2. URL scheme 和 host 走 allowlist，不接受任意 http/https 页面进入远控 WebView。
3. session id 按 UUID 解析失败时直接进入错误页，不加载 WebView。
4. query 参数原样带入 WebView URL，但日志只记录 key 列表和 hash。
5. 冷启动和热启动复用同一个 parser。
6. 为 parser 准备测试 fixture：合法 session、带 query、缺 session、错误 UUID、错误 host、大小写路径。

## 日志

- `mobile_link_open_received`
- `mobile_link_parse_succeeded`
- `mobile_link_parse_failed`
- `mobile_link_load_url_created`

字段：

- `source`
- `session_id_hash`
- `original_url_hash`
- `query_keys`
- `failure_reason`

禁止记录完整 URL、token、cookie、用户私有 query value。

## 测试

- Kotlin unit test 覆盖 parser。
- Android instrumentation 覆盖外部 intent 冷启动。
- 热启动测试覆盖已有 Activity 收到新链接。
- 回归用例与 Rust parser fixture 保持同名，便于后续做跨语言一致性测试。
- redaction test 确认敏感 query 不进入日志、最近会话或诊断包。

## 退出标准

- 所有合法分享链接都能生成确定的 WebView load URL。
- 所有非法链接都有稳定错误码。
- 日志足以区分解析失败、域名拒绝和 session id 无效。
- 最近会话不保存敏感凭据。
