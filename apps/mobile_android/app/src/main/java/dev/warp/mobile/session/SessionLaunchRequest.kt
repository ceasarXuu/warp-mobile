package dev.warp.mobile.session

data class SessionLaunchRequest(
    val originalUrlHash: String,
    val loadUrl: String,
    val redactedUrl: String,
    val sessionId: String,
    val sessionIdHash: String,
    val queryKeys: Set<String>,
    val source: LaunchSource,
    val useFakePage: Boolean,
)
