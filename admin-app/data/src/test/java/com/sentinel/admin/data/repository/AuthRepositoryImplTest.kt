package com.sentinel.admin.data.repository

import com.sentinel.admin.domain.repository.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [AuthRepository] interface contract.
 *
 * Since [AuthRepositoryImpl] requires Android Context (EncryptedSharedPreferences),
 * these tests verify the contract using an in-memory test double.
 * The real EncryptedSharedPreferences implementation is verified
 * in instrumented tests.
 */
class AuthRepositoryImplTest {

    /** In-memory test double for verifying interface contract. */
    private class InMemoryAuthRepository : AuthRepository {
        @Volatile
        private var token: String? = null
        override fun getToken(): String? = token
        override fun saveToken(token: String) { this.token = token }
        override fun clearToken() { token = null }
    }

    private val repo: AuthRepository = InMemoryAuthRepository()

    @Test
    fun `initially returns null token`() {
        assertNull(repo.getToken())
    }

    @Test
    fun `saveToken then getToken returns saved value`() {
        repo.saveToken("jwt-123")
        assertEquals("jwt-123", repo.getToken())
    }

    @Test
    fun `saveToken overwrites previous value`() {
        repo.saveToken("first")
        repo.saveToken("second")
        assertEquals("second", repo.getToken())
    }

    @Test
    fun `clearToken removes stored token`() {
        repo.saveToken("jwt-456")
        repo.clearToken()
        assertNull(repo.getToken())
    }

    @Test
    fun `clearToken on empty is no-op`() {
        repo.clearToken()
        assertNull(repo.getToken())
    }
}
