package dev.warp.mobile.webview

object WebViewAuthScripts {
    fun authHandoffScript(): String {
        return """
            (function() {
              const bootstrapKey = 'warpAndroidSessionBootstrap';
              const firebaseApiKey = 'AIzaSyBdy3O3S9hrdayLJxJ7mriBR4qgUaUygAs';
              const androidAuthHandoff = {
                webFallback: null,
                readToken: function() {
                  try {
                    if (!window.WarpAndroidAuthHandoff) return null;
                    return window.WarpAndroidAuthHandoff.refreshToken();
                  } catch (_) {
                    return null;
                  }
                },
                saveToken: function(token) {
                  try {
                    if (token && window.WarpAndroidAuthHandoff) {
                      window.WarpAndroidAuthHandoff.updateRefreshToken(token);
                    }
                  } catch (_) {}
                },
                handoff: function() {
                  const token = androidAuthHandoff.readToken();
                  if (token) return token;
                  if (typeof androidAuthHandoff.webFallback === 'function') {
                    try {
                      return androidAuthHandoff.webFallback();
                    } catch (_) {
                      return null;
                    }
                  }
                  return null;
                }
              };

              Object.defineProperty(window, 'warpUserHandoff', {
                configurable: true,
                get: function() {
                  return androidAuthHandoff.handoff;
                },
                set: function(value) {
                  androidAuthHandoff.webFallback = value;
                }
              });

              async function establishWarpSession() {
                if (window.location.origin !== 'https://app.warp.dev') return;
                const refreshToken = androidAuthHandoff.readToken();
                if (!refreshToken) return;
                const state = window.sessionStorage.getItem(bootstrapKey);
                if (state === 'done') return;
                if (state && state.indexOf('reauth_requested:') === 0) {
                  const requestedAt = Number(state.slice('reauth_requested:'.length));
                  if (!Number.isNaN(requestedAt) && Date.now() - requestedAt < 15000) return;
                }
                if (state && state.indexOf('running:') === 0) {
                  const startedAt = Number(state.slice('running:'.length));
                  if (!Number.isNaN(startedAt) && Date.now() - startedAt < 15000) return;
                }
                window.sessionStorage.setItem(bootstrapKey, 'running:' + Date.now());
                try {
                  const current = await fetch('/auth/session', { cache: 'no-store', credentials: 'include' });
                  if (current.ok) {
                    window.sessionStorage.setItem(bootstrapKey, 'done');
                    return;
                  }
                  if (current.status !== 401) {
                    window.sessionStorage.setItem(bootstrapKey, 'session_check_' + current.status);
                    return;
                  }

                  const tokenResponse = await fetch(
                    'https://securetoken.googleapis.com/v1/token?key=' + encodeURIComponent(firebaseApiKey),
                    {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                      body: 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(refreshToken)
                    }
                  );
                  if (!tokenResponse.ok) {
                    console.warn('Warp Android auth bootstrap token exchange failed', tokenResponse.status);
                    window.sessionStorage.setItem(bootstrapKey, 'token_' + tokenResponse.status);
                    return;
                  }
                  const tokenJson = await tokenResponse.json();
                  if (tokenJson.refresh_token) androidAuthHandoff.saveToken(tokenJson.refresh_token);
                  if (!tokenJson.id_token) {
                    window.sessionStorage.setItem(bootstrapKey, 'missing_id_token');
                    return;
                  }
                  try {
                    const claims = JSON.parse(atob(tokenJson.id_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
                    console.info('Warp Android auth bootstrap token exchanged', claims.aud || '', claims.iss || '');
                  } catch (_) {
                    console.info('Warp Android auth bootstrap token exchanged');
                  }

                  const sessionResponse = await fetch('/api/v1/oauth/session', {
                    method: 'POST',
                    headers: {
                      Authorization: 'Bearer ' + tokenJson.id_token,
                      'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ rememberMe: true }),
                    credentials: 'include'
                  });
                  if (!sessionResponse.ok) {
                    let responseText = '';
                    try {
                      responseText = (await sessionResponse.text()).slice(0, 240);
                    } catch (_) {}
                    console.warn('Warp Android auth bootstrap session failed', sessionResponse.status, responseText);
                    if (sessionResponse.status === 401 && responseText.indexOf('Recent sign in required') >= 0) {
                      window.sessionStorage.setItem(bootstrapKey, 'reauth_requested:' + Date.now());
                      try {
                        window.WarpAndroidAuthHandoff.requestBrowserLogin('recent_sign_in_required');
                      } catch (_) {}
                      return;
                    }
                    window.sessionStorage.setItem(bootstrapKey, 'session_' + sessionResponse.status);
                    return;
                  }
                  console.info('Warp Android auth bootstrap session established');
                  window.sessionStorage.setItem(bootstrapKey, 'done');
                  window.location.replace(window.location.href);
                } catch (error) {
                  window.sessionStorage.setItem(bootstrapKey, 'error');
                  console.warn('Warp Android auth bootstrap failed', error && error.message ? error.message : error);
                }
              }

              establishWarpSession();
            })();
        """.trimIndent()
    }
}
