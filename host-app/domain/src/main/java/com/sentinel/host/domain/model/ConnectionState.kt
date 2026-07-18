package com.sentinel.host.domain.model

/**
 * Connection lifecycle state machine.
 *
 * Disconnected → Connecting → Connected → Authenticating →
 * Authenticated → Registering → Ready
 *
 * Failure may occur from any state.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object Authenticating : ConnectionState
    data object Authenticated : ConnectionState
    data object Registering : ConnectionState
    data object Ready : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

