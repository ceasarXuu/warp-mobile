package dev.warp.mobile.shell

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.warp.mobile.auth.AuthHandoffProvider
import dev.warp.mobile.observability.MobileEventLogger
import dev.warp.mobile.tabs.RemoteTab
import dev.warp.mobile.webview.RemoteSessionWebView

@Composable
internal fun RemoteSessionWebViewHost(
    tab: RemoteTab,
    webNavigationId: Long,
    logger: MobileEventLogger,
    authHandoffProvider: AuthHandoffProvider,
    onAuthRequired: (String) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onWebViewReleased: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    key(tab.id, webNavigationId) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                RemoteSessionWebView.create(
                    context = context,
                    request = tab.request,
                    logger = logger,
                    authHandoffProvider = authHandoffProvider,
                    onAuthRequired = onAuthRequired,
                ).also(onWebViewReady)
            },
            update = { webView ->
                onWebViewReady(webView)
                RemoteSessionWebView.update(webView, tab.request, logger)
            },
            onRelease = { webView ->
                onWebViewReleased(webView)
                RemoteSessionWebView.destroy(webView, logger, "compose_release")
            },
        )
    }
}
