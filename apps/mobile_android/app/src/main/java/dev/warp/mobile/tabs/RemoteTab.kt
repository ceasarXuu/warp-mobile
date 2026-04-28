package dev.warp.mobile.tabs

import dev.warp.mobile.session.SessionLaunchRequest

data class RemoteTab(
    val id: String,
    val request: SessionLaunchRequest,
    val createdAtEpochMillis: Long,
) {
    val label: String
        get() = request.sessionIdHash.take(8)
}
