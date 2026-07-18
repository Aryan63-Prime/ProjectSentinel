package com.sentinel.admin.domain.repository

/**
 * Token storage for Admin authentication.
 *
 * Stores and retrieves the JWT token used for both
 * WebSocket AUTH and REST API Authorization headers.
 */
interface AuthRepository {
    /** Retrieves the stored JWT token, or null if not authenticated. */
    fun getToken(): String?

    /** Stores the JWT token. */
    fun saveToken(token: String)

    /** Clears the stored token. */
    fun clearToken()
}
