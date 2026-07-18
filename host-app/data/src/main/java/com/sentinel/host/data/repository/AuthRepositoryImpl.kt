package com.sentinel.host.data.repository

import com.sentinel.host.data.remote.SequenceGenerator
import com.sentinel.host.data.remote.protocol.MessageSerializer
import com.sentinel.host.domain.model.ConnectionEvent
import com.sentinel.host.domain.repository.AuthRepository
import com.sentinel.host.domain.repository.ConnectionRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Sends AUTH message and awaits AUTH_ACK event with timeout.
 * No token persistence — that's SessionManager's job.
 */
class AuthRepositoryImpl(
    private val connectionRepository: ConnectionRepository,
    private val messageSerializer: MessageSerializer,
    private val sequenceGenerator: SequenceGenerator
) : AuthRepository {

    companion object {
        private const val AUTH_TIMEOUT_MS = 10_000L
    }

    override suspend fun authenticate(token: String): Result<Boolean> {
        val json = messageSerializer.serializeAuth(token, sequenceGenerator.next())

        if (!connectionRepository.sendText(json)) {
            return Result.failure(Exception("Failed to send AUTH message"))
        }

        return try {
            withTimeout(AUTH_TIMEOUT_MS) {
                val event = connectionRepository.events.first { event ->
                    event is ConnectionEvent.Authenticated || event is ConnectionEvent.Error
                }
                when (event) {
                    is ConnectionEvent.Authenticated -> Result.success(true)
                    is ConnectionEvent.Error -> Result.failure(
                        AuthenticationException(event.code, event.message)
                    )
                    else -> Result.failure(Exception("Unexpected event"))
                }
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(AuthenticationException(0, "Authentication timeout"))
        }
    }
}

class AuthenticationException(val code: Int, override val message: String) : Exception(message)
