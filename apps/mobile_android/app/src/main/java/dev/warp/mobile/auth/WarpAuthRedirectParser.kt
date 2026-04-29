package dev.warp.mobile.auth

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class WarpAuthRedirect(
    val refreshToken: String,
    val state: String?,
    val userUid: String?,
)

class WarpAuthRedirectException(val reason: String) : Exception(reason)

object WarpAuthRedirectParser {
    const val RedirectScheme = "warposs"
    const val RedirectHost = "auth"
    const val RedirectPath = "/desktop_redirect"
    private const val LoginBaseUrl = "https://app.warp.dev/login/remote"
    private const val FreshLoginBaseUrl = "https://app.warp.dev/login_options/remote"

    fun buildLoginUrl(state: String, forceFreshCredential: Boolean = false): String {
        val baseUrl = if (forceFreshCredential) FreshLoginBaseUrl else LoginBaseUrl
        return "$baseUrl?scheme=${RedirectScheme.encodeQuery()}&state=${state.encodeQuery()}"
    }

    fun parse(rawUrl: String): WarpAuthRedirect {
        val uri = try {
            URI(rawUrl)
        } catch (_: IllegalArgumentException) {
            throw WarpAuthRedirectException("malformed_url")
        }

        val scheme = uri.scheme?.lowercase(Locale.US)
            ?: throw WarpAuthRedirectException("unsupported_scheme")
        if (scheme != RedirectScheme) throw WarpAuthRedirectException("unsupported_scheme")
        if (uri.host != RedirectHost) throw WarpAuthRedirectException("unsupported_host")
        if (uri.path != RedirectPath) throw WarpAuthRedirectException("unsupported_path")

        val query = parseQuery(uri.rawQuery)
        val refreshToken = query["refresh_token"]?.takeIf { it.isNotBlank() }
            ?: throw WarpAuthRedirectException("missing_refresh_token")
        return WarpAuthRedirect(
            refreshToken = refreshToken,
            state = query["state"],
            userUid = query["user_uid"],
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&')
            .filter { it.isNotBlank() }
            .associate { item ->
                val parts = item.split('=', limit = 2)
                parts[0].decodeQuery() to parts.getOrElse(1) { "" }.decodeQuery()
            }
    }

    private fun String.encodeQuery(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    }

    private fun String.decodeQuery(): String {
        return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    }
}
