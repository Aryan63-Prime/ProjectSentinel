package com.sentinel.host.domain.model

/**
 * Events emitted during the connection lifecycle.
 * The supervisor consumes events and derives state.
 *
 * Supervisor → Events → State
 * (not Supervisor → State → Guess)
 */
sealed interface ConnectionEvent {
    data object Connected : ConnectionEvent
    data object Authenticated : ConnectionEvent
    data object Registered : ConnectionEvent
    data object HeartbeatAck : ConnectionEvent
    data class Error(val code: Int, val message: String) : ConnectionEvent
    data object Disconnected : ConnectionEvent

    // Sprint A6: Reconnection events
    data class Reconnecting(val attempt: Int, val delayMs: Long) : ConnectionEvent
    data class ReconnectFailed(val attempt: Int, val reason: String) : ConnectionEvent
    data object ReconnectExhausted : ConnectionEvent
}
