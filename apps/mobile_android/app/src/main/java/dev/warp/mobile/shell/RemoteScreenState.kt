package dev.warp.mobile.shell

import dev.warp.mobile.session.SessionLaunchRequest

sealed interface RemoteScreenState {
    data object Loading : RemoteScreenState
    data class Ready(val request: SessionLaunchRequest) : RemoteScreenState
    data class Error(val reason: String, val rawUrl: String) : RemoteScreenState
}
