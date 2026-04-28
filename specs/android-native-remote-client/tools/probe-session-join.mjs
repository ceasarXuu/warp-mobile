#!/usr/bin/env node

const DEFAULT_SERVER = 'wss://sessions.app.warp.dev';
const DEFAULT_TIMEOUT_MS = 15000;

function usage() {
  console.error(
    [
      'Usage: node probe-session-join.mjs <session-url-or-id> [--server=wss://...] [--version=v...] [--pwd=...] [--timeout-ms=15000] [--require-message] [--require-join]',
      '',
      'Optional auth:',
      '  WARP_FIREBASE_ID_TOKEN=<short-lived Firebase ID token>',
      '',
      'The probe exits 0 when the websocket opens and the Initialize message is sent.',
      'Use --require-message to require a first downstream protocol message.',
      'Use --require-join to make FailedToJoin a non-zero result.',
    ].join('\n'),
  );
}

function parseArgs(argv) {
  const options = {
    server: DEFAULT_SERVER,
    timeoutMs: DEFAULT_TIMEOUT_MS,
    requireJoin: false,
    requireMessage: false,
    version: undefined,
    pwd: undefined,
    target: undefined,
  };

  for (const arg of argv) {
    if (arg === '--help' || arg === '-h') {
      usage();
      process.exit(0);
    } else if (arg === '--require-join') {
      options.requireJoin = true;
      options.requireMessage = true;
    } else if (arg === '--require-message') {
      options.requireMessage = true;
    } else if (arg.startsWith('--server=')) {
      options.server = arg.slice('--server='.length);
    } else if (arg.startsWith('--version=')) {
      options.version = arg.slice('--version='.length);
    } else if (arg.startsWith('--pwd=')) {
      options.pwd = arg.slice('--pwd='.length);
    } else if (arg.startsWith('--timeout-ms=')) {
      options.timeoutMs = Number(arg.slice('--timeout-ms='.length));
    } else if (!options.target) {
      options.target = arg;
    } else {
      throw new Error(`Unexpected argument: ${arg}`);
    }
  }

  if (!options.target) {
    throw new Error('Missing session URL or session ID.');
  }
  if (!Number.isFinite(options.timeoutMs) || options.timeoutMs <= 0) {
    throw new Error(`Invalid timeout: ${options.timeoutMs}`);
  }
  return options;
}

function parseSessionTarget(target) {
  const directUuid = target.match(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  );
  if (directUuid) {
    return { sessionId: target, query: new URLSearchParams() };
  }

  const url = new URL(target);
  const segments = url.pathname.split('/').filter(Boolean);
  const sessionIndex = segments.findIndex((segment) => segment === 'session');
  const sessionId = sessionIndex >= 0 ? segments[sessionIndex + 1] : segments.at(-1);
  if (
    !sessionId ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sessionId)
  ) {
    throw new Error(`Could not parse a session UUID from: ${target}`);
  }
  return { sessionId, query: new URLSearchParams(url.search) };
}

function buildJoinUrl(options) {
  const { sessionId, query } = parseSessionTarget(options.target);
  if (options.pwd !== undefined) {
    query.set('pwd', options.pwd);
  }
  if (options.version) {
    query.set('version', options.version);
  }

  const server = options.server.replace(/\/+$/, '');
  const queryString = query.toString();
  const url = `${server}/sessions/join/${sessionId}${queryString ? `?${queryString}` : ''}`;
  return { sessionId, url };
}

function logEvent(event, fields = {}) {
  console.log(JSON.stringify({ event, ...fields }));
}

function firstMessageKind(message) {
  if (!message || typeof message !== 'object' || Array.isArray(message)) {
    return 'Unknown';
  }
  return Object.keys(message)[0] ?? 'Unknown';
}

async function run() {
  const options = parseArgs(process.argv.slice(2));
  if (typeof WebSocket !== 'function') {
    throw new Error('This probe requires a Node runtime with built-in WebSocket support.');
  }

  const { sessionId, url } = buildJoinUrl(options);
  const token = process.env.WARP_FIREBASE_ID_TOKEN || null;
  const anonymousId = crypto.randomUUID();

  logEvent('probe_session_join_started', {
    session_id_hash: await hashText(sessionId),
    server: new URL(url).origin,
    has_token: Boolean(token),
    has_pwd: new URL(url).searchParams.has('pwd'),
  });

  const ws = new WebSocket(url);
  const done = new Promise((resolve) => {
    let settled = false;
    let closingAfterFirstMessage = false;
    let opened = false;
    let initializeSent = false;
    const settle = (code) => {
      if (!settled) {
        settled = true;
        resolve(code);
      }
    };

    const timeout = setTimeout(() => {
      logEvent('probe_session_join_timeout', { ready_state: ws.readyState });
      try {
        ws.close();
      } catch {
        // Best-effort close only.
      }
      settle(4);
    }, options.timeoutMs);

    ws.addEventListener('open', () => {
      opened = true;
      logEvent('probe_session_join_socket_opened');
      const payload = {
        Initialize: {
          viewer_id: null,
          user_id: {
            anonymous_id: anonymousId,
            firebase_id_token: token,
          },
          last_received_event_no: null,
          latest_block_id: null,
          telemetry_context: null,
          feature_support: {
            supports_agent_view: false,
            supports_full_role: true,
            supports_full_role_for_real: true,
          },
        },
      };
      ws.send(JSON.stringify(payload));
      initializeSent = true;
      logEvent('probe_session_join_initialize_sent', { has_token: Boolean(token) });
    });

    ws.addEventListener('message', (event) => {
      clearTimeout(timeout);
      let parsed;
      try {
        parsed = JSON.parse(String(event.data));
      } catch {
        parsed = null;
      }
      const kind = firstMessageKind(parsed);
      const reason = parsed?.FailedToJoin?.reason;
      logEvent('probe_session_join_first_message', { kind, reason });
      closingAfterFirstMessage = true;
      ws.close(1000, 'probe complete');
      settle(options.requireJoin && kind !== 'JoinedSuccessfully' ? 3 : 0);
    });

    ws.addEventListener('error', () => {
      if (closingAfterFirstMessage) {
        return;
      }
      clearTimeout(timeout);
      logEvent('probe_session_join_socket_error', { opened, initialize_sent: initializeSent });
      settle(opened && initializeSent && !options.requireMessage ? 0 : 2);
    });

    ws.addEventListener('close', (event) => {
      if (settled || closingAfterFirstMessage) {
        return;
      }
      clearTimeout(timeout);
      logEvent('probe_session_join_socket_closed', {
        opened,
        initialize_sent: initializeSent,
        code: event.code,
        reason: event.reason,
      });
      settle(opened && initializeSent && !options.requireMessage ? 0 : 5);
    });
  });

  process.exitCode = await done;
}

async function hashText(value) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .slice(0, 8)
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

run().catch((error) => {
  console.error(JSON.stringify({ event: 'probe_session_join_failed', message: error.message }));
  process.exitCode = 1;
});
