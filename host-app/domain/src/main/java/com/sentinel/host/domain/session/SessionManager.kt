package com.sentinel.host.domain.session

/**
 * Single source of truth for JWT and session state.
 * Nothing else should know where the token lives.
 *
 * Implementation uses EncryptedSharedPreferences.
 */
interface SessionManager {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
    fun saveServerUrl(url: String)
    fun getServerUrl(): String?
    fun hasSession(): Boolean
    fun clear()
}
