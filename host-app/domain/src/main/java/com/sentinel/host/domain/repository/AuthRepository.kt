package com.sentinel.host.domain.repository

/**
 * Sends AUTH message and awaits server response.
 * Token persistence is [SessionManager]'s responsibility.
 */
interface AuthRepository {
    suspend fun authenticate(token: String): Result<Boolean>
}

