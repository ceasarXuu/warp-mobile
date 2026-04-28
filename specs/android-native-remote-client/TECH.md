# Android Native Remote Client Feasibility

## Verdict

Building a native Android remote-session client is technically plausible, but it is not a small replacement for the WebView shell. The hosted session-sharing server already exposes a non-Web transport and the open source client has the Rust protocol path, but Android must port or wrap enough of desktop Warp's auth, session-sharing viewer, terminal model, renderer, input, and permission behavior to become a real client.

The feasible path is a staged spike, not a direct Kotlin rewrite:

1. Reuse the Rust protocol/auth types through a small Android-facing core where possible.
2. Prove authenticated join against the hosted server before building native terminal UI.
3. Treat native rendering/input as a second milestone after join and first-frame replay are proven.

## Evidence

- Web session links are already mapped to native shared-session intents in desktop code. `join_link` emits `https://app.warp.dev/session/{session_id}` for bundled builds, and web intent parsing maps `/session/{uuid}` to `warp://shared_session/{session_id}`.
  - `app/src/terminal/shared_session/mod.rs:302`
  - `app/src/uri/web_intent_parser.rs:50`
- The session-sharing server is separate from the web app. Production config points to `wss://sessions.app.warp.dev`, and the viewer connects to `/sessions/join/{session_id}`.
  - `crates/warp_core/src/channel/config.rs:44`
  - `app/src/terminal/shared_session/viewer/network.rs:311`
- The protocol is explicit JSON over WebSocket. A viewer sends `Initialize`, receives `JoinedSuccessfully`/`FailedToJoin`, then consumes `OrderedTerminalEvent` frames.
  - `session-sharing-protocol/src/viewer.rs:85`
  - `session-sharing-protocol/src/viewer.rs:107`
  - `session-sharing-protocol/src/viewer.rs:316`
- Viewer terminal replay is not just a stream dump. The desktop viewer loads scrollback, respects sharer window size, buffers ordered events, decompresses PTY byte batches with LZ4, and parses ANSI bytes into `TerminalModel`.
  - `app/src/terminal/shared_session/viewer/event_loop.rs:63`
  - `app/src/terminal/shared_session/viewer/event_loop.rs:131`
- Input is permissioned by role. The viewer only sends input updates and command execution when the local shared-session status can execute; long-running PTY writes are a separate request path.
  - `app/src/terminal/shared_session/viewer/terminal_manager.rs:1246`
  - `app/src/terminal/shared_session/viewer/network.rs:807`
  - `app/src/terminal/shared_session/viewer/network.rs:844`
- Headless device auth already exists and avoids embedded provider login. It requests `/api/v1/oauth/device/auth`, polls `/api/v1/oauth/token`, exchanges the custom token for Firebase credentials, and persists refresh-backed auth state.
  - `app/src/server/server_api.rs:473`
  - `app/src/auth/auth_manager.rs:268`
  - `app/src/server/server_api/auth.rs:548`
  - `app/src/auth/auth_state.rs:76`

## Live Probe Result

I ran a non-authenticated probe against the real user-provided link:

```powershell
node specs\android-native-remote-client\tools\probe-session-join.mjs `
  https://app.warp.dev/session/{uuid} `
  --version=v0.2026.04.27.15.32.stable_02
```

Initial observed result:

```json
{"event":"probe_session_join_socket_opened"}
{"event":"probe_session_join_initialize_sent","has_token":false}
{"event":"probe_session_join_first_message","kind":"FailedToJoin","reason":"Invalid"}
```

Repeated unauthenticated probes can also close after `Initialize` with a socket error instead of returning a structured `FailedToJoin`. Treat both as a failed unauthenticated join; use `--require-message` only when the server is expected to return a downstream frame.

Interpretation:

- The hosted WebSocket endpoint is reachable from this machine.
- The server accepts the WebSocket and the desktop-shaped `Initialize` payload can be sent.
- The session could not be joined reliably with only a UUID and no Firebase ID token/session secret.
- The next proof point is authenticated join, not WebView debugging.

The web page itself returns the SPA shell for the same link and loads bot/risk SDKs (`recaptcha`, `Verisoul`), which matches the earlier login/captcha pain. Native protocol join would bypass the web login UI only after native auth is solved.

## Hard Problems

### 1. Auth Without WebView

Use device authorization first. It is the best Android-only path because it opens a trusted browser for login but gives the app its own Firebase-backed credentials after approval.

Development contract:

- Android starts a native device-auth flow.
- User completes login in browser using the verification URL/code.
- Android stores the resulting refresh token in Android Keystore-backed storage.
- Session joins use short-lived Firebase ID tokens in `UserID.firebase_id_token`.

Open validation:

- Confirm the server accepts device-auth credentials for session-sharing viewer joins from Android.
- Confirm Google/GitHub login through device auth has lower captcha friction than embedded WebView.
- Confirm account/team ACLs are sufficient for the given session link.

### 2. Link Identity And Access

The protocol still defines `SessionSecret` and `JoinSessionLinkArgs` with `pwd`, while current link generation can emit a bare `/session/{session_id}` URL. That means access can depend on either:

- legacy link secret query (`pwd`),
- server-side ACL and authenticated Firebase identity,
- or both, depending on feature flag/version.

Development contract:

- Android link parser must preserve query parameters from Warp session URLs.
- The native join layer must pass through `pwd`, `preview`, and `version` when present.
- The client must classify `FailedToJoin` into actionable UI: invalid link, no access, not found, max participants, or unauthenticated.

### 3. Protocol Port

Do not hand-write the protocol in Kotlin unless Rust integration proves impossible. The protocol crate is already pinned from Git:

```toml
session-sharing-protocol = { git = "https://github.com/warpdotdev/session-sharing-protocol.git", rev = "3a12b871dfd1019a66057e4d9b7d5c812b73ee8c" }
```

Preferred implementation:

- Create a small Rust `mobile_session_core` wrapper around `session-sharing-protocol`.
- Expose a narrow Android API via UniFFI/JNI:
  - parse session link,
  - open viewer socket,
  - send initialize/heartbeat,
  - emit typed downstream events,
  - send role/input/resize/write requests.
- Keep auth token acquisition injectable so Android owns secure storage and UX.

### 4. Terminal Rendering

The first native renderer can be intentionally narrower than desktop Warp, but it still needs a real terminal model. A raw TextView transcript will break cursor movement, alternate screen, ANSI styles, TUI apps, and remote control.

Preferred implementation:

- Reuse `warp_terminal` parsing/model code in Rust.
- Expose a compact render snapshot to Compose:
  - rows,
  - cells,
  - style IDs,
  - cursor,
  - selection,
  - scrollback metadata.
- Render in Compose Canvas or a custom Android View using Warp mobile design tokens.

Spike exit criteria:

- `JoinedSuccessfully` scrollback produces visible rows.
- `OrderedTerminalEvent::PtyBytesRead` updates rows incrementally.
- Resize events update terminal dimensions and send `ReportTerminalSize`.

### 5. Input And Keyboard

Astropath keyboard logic remains useful, but the output target changes. Instead of sending JS bridge messages to WebView, native input must call protocol APIs:

- printable/edit-buffer changes -> `UpdateInput`,
- execute -> `ExecuteCommand`,
- long-running process bytes -> `WriteToPty`,
- terminal viewport changes -> `ReportTerminalSize`,
- permission escalation -> `RequestRole`.

Development contract:

- The keyboard module must consume session role state.
- Read-only sessions keep navigation/selection but disable execution keys.
- Executor/full sessions can send commands and PTY writes.
- All sends must log session-hash, role, request type, and rejection reason without logging command text by default.

### 6. Android Lifecycle

Native session state replaces WebView persistence:

- tabs persist session URLs and selected tab,
- auth persists in Android secure storage,
- viewer sockets reconnect with participant ID and `last_received_event_no`,
- foreground/background transitions control heartbeat and reconnect policy,
- process death restores tabs but must rejoin sockets from scratch.

## Module Plan

### `mobile_session_core` Rust Module

Responsibilities:

- own WebSocket transport to session-sharing server,
- serialize/deserialize protocol messages,
- keep viewer connection state,
- perform ordered event buffering,
- expose terminal model updates.

Validation:

- unit tests for JSON compatibility against `session-sharing-protocol`,
- local mock WebSocket join flow,
- live `FailedToJoin` probe against hosted server,
- authenticated `JoinedSuccessfully` probe after device auth lands.

### Android Auth Module

Responsibilities:

- device-auth request and polling UX,
- Custom Tabs/browser launch for verification URL,
- refresh-token secure storage,
- short-lived ID token refresh.

Validation:

- device-auth code appears and browser opens,
- approved login persists refresh token,
- ID token refresh succeeds after app restart,
- no provider login page is rendered inside WebView/native terminal.

### Session Link Module

Responsibilities:

- parse `https://app.warp.dev/session/{uuid}`,
- preserve `pwd`, `preview`, and future query params,
- normalize into native session targets,
- hash session IDs for logs.

Validation:

- unit tests for UUID validation and query preservation,
- real link maps to native target without logging raw URL.

### Native Terminal UI Module

Responsibilities:

- render terminal snapshots from Rust core,
- support scrollback, cursor, selection, resize,
- apply Warp mobile design tokens and desktop-like dark terminal styling.

Validation:

- screenshot tests for empty/loading/reader/executor/error states,
- replay fixture renders deterministic terminal rows,
- real device smoke verifies nonblank terminal after first joined frame.

### Native Keyboard/Input Module

Responsibilities:

- keep Astropath band/gesture behavior,
- route actions to session-core APIs,
- enforce role-based enabled/disabled states,
- keep built-in/system keyboard exclusivity.

Validation:

- unit tests for action-to-protocol mapping,
- real device key smoke for printable, modifier, navigation, enter, backspace,
- rejection logs surface protocol failures.

### Observability Module

Responsibilities:

- structured logs for auth, connect, join, first frame, input, reconnect, role changes, and failures,
- no raw session URLs, tokens, provider URLs, or command text in default logs,
- one reusable host probe for session-sharing transport.

Validation:

- `probe-session-join.mjs` returns first server message,
- Android logcat contains connect/join/rejection events with session hash only.

## Spike Sequence

1. **S0 transport probe**: use `probe-session-join.mjs` to confirm WebSocket reachability and first protocol response. Done unauthenticated; result is `FailedToJoin Invalid`.
2. **S1 Android device auth**: implement native device-auth flow and secure credential storage.
3. **S2 authenticated join CLI/Rust spike**: use the same token shape Android will use to join the supplied session link and capture first downstream message.
4. **S3 Rust core on Android**: package the protocol wrapper in Android and emit raw downstream events to logcat.
5. **S4 terminal replay**: render scrollback and PTY byte events natively.
6. **S5 input**: send role request, command/input/update/write requests from Astropath keyboard actions.

Do not start native renderer work before S2 proves authenticated hosted join. Without S2, the project can only produce a polished local terminal mock.

## Remaining Unknowns

- Whether a mobile device-auth principal can join every web share link or only sessions shared to that account/team.
- Whether bare `/session/{uuid}` links now rely entirely on ACLs, while `pwd` links remain legacy.
- Whether hosted server enforces client version/feature flags for Android clients.
- Whether Android can use the same Firebase refresh-token exchange endpoints without additional app attestation constraints.
- How much desktop `TerminalModel` can be reused without pulling in large Warp UI dependencies.
