package dev.warp.mobile.session

import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object SessionLinkParser {
    private val allowedHosts = setOf("app.warp.dev", "debug.warp.local", "localhost", "127.0.0.1")
    private val sensitiveQueryKeys = setOf("pwd", "password", "token", "auth", "key", "code", "state")

    fun parse(rawUrl: String, source: LaunchSource): SessionLaunchRequest {
        val uri = try {
            URI(rawUrl)
        } catch (_: IllegalArgumentException) {
            throw SessionLinkParseException("malformed_url")
        }

        val scheme = uri.scheme?.lowercase(Locale.US)
            ?: throw SessionLinkParseException("unsupported_scheme")
        if (scheme != "https" && scheme != "http") {
            throw SessionLinkParseException("unsupported_scheme")
        }

        val host = uri.host?.lowercase(Locale.US)
            ?: throw SessionLinkParseException("unsupported_origin")
        if (host !in allowedHosts) {
            throw SessionLinkParseException("unsupported_origin")
        }
        if (scheme == "http" && host !in setOf("localhost", "127.0.0.1")) {
            throw SessionLinkParseException("unsupported_scheme")
        }

        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        if (segments.size != 2 || segments[0] != "session") {
            throw SessionLinkParseException("unsupported_path")
        }

        val sessionId = segments[1]
        try {
            UUID.fromString(sessionId)
        } catch (_: IllegalArgumentException) {
            throw SessionLinkParseException("invalid_session_id")
        }

        val queryPairs = parseQuery(uri.rawQuery)
        val redactedQuery = queryPairs.joinToString("&") { (key, value) ->
            val redactedValue = if (key.lowercase(Locale.US) in sensitiveQueryKeys) {
                "REDACTED"
            } else {
                value
            }
            "$key=$redactedValue"
        }
        val redactedUrl = buildString {
            append(scheme).append("://").append(host).append(uri.path)
            if (redactedQuery.isNotEmpty()) append('?').append(redactedQuery)
        }

        return SessionLaunchRequest(
            originalUrlHash = sha256(rawUrl),
            loadUrl = uri.toASCIIString(),
            redactedUrl = redactedUrl,
            sessionId = sessionId,
            sessionIdHash = sha256(sessionId),
            queryKeys = queryPairs.map { it.first }.toSet(),
            source = source,
            useFakePage = host == "debug.warp.local",
        )
    }

    private fun parseQuery(rawQuery: String?): List<Pair<String, String>> {
        if (rawQuery.isNullOrBlank()) return emptyList()
        return rawQuery.split('&')
            .filter { it.isNotBlank() }
            .map {
                val parts = it.split('=', limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
