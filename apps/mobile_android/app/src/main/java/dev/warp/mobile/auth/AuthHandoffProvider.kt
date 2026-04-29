package dev.warp.mobile.auth

interface AuthHandoffProvider {
    fun refreshTokenForHandoff(): String?
    fun saveRefreshTokenFromHandoff(token: String)
}
