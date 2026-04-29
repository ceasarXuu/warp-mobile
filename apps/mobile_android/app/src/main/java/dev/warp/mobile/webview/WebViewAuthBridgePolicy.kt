package dev.warp.mobile.webview

import dev.warp.mobile.session.SessionLaunchRequest
import java.net.URI
import java.util.Locale

object WebViewAuthBridgePolicy {
    private const val HostedWarpAppHost = "app.warp.dev"
    private val debugSessionHosts = setOf("debug.warp.local", "localhost", "127.0.0.1")
    private val sessionHostsWithoutTokenBridge = debugSessionHosts + HostedWarpAppHost
    private val hostedWarpHostSuffixes = setOf(".app.warp.dev")

    val authHandoffOrigins: Set<String> = setOf("https://$HostedWarpAppHost")

    fun shouldExposeTokenBridge(request: SessionLaunchRequest): Boolean {
        if (request.useFakePage) return false
        val uri = runCatching { URI(request.loadUrl) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            normalizeHost(uri.host) == HostedWarpAppHost
    }

    fun shouldInjectAuthHandoff(host: String, tokenBridgeExposed: Boolean): Boolean {
        return tokenBridgeExposed && normalizeHost(host) == HostedWarpAppHost
    }

    fun isAllowedNavigationHost(host: String, tokenBridgeExposed: Boolean): Boolean {
        val normalizedHost = normalizeHost(host)
        if (normalizedHost.isBlank()) return false
        if (tokenBridgeExposed) {
            return normalizedHost == HostedWarpAppHost
        }
        return normalizedHost in sessionHostsWithoutTokenBridge ||
            hostedWarpHostSuffixes.any { suffix -> normalizedHost.endsWith(suffix) }
    }

    fun isAllowedAuthContinuationHost(host: String): Boolean {
        return normalizeHost(host) == HostedWarpAppHost
    }

    fun isDebugOrLocalHost(host: String): Boolean {
        return normalizeHost(host) in debugSessionHosts
    }

    private fun normalizeHost(host: String?): String {
        return host.orEmpty().trim().lowercase(Locale.US)
    }
}
