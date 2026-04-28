package dev.warp.mobile.shell

import dev.warp.mobile.tabs.RemoteTab

sealed interface RemoteScreenState {
    data object Loading : RemoteScreenState
    data object Welcome : RemoteScreenState
    data class Ready(val tabs: List<RemoteTab>, val selectedTabId: String) : RemoteScreenState
    data class Error(val reason: String, val rawUrl: String) : RemoteScreenState
}
