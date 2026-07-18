package com.sentinel.admin.domain.model

/**
 * Connection lifecycle state machine.
 *
 * Disconnected → Connecting → TransportConnected → Authenticating →
 * Ready
 *
 * TransportConnected means the WebSocket is open but the application
 * is not yet usable. Ready means authentication succeeded and the
 * admin can observe devices.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState

    /**
     * WebSocket transport is connected but authentication has not
     * completed. The application is NOT usable in this state.
     */
    data object TransportConnected : ConnectionState

    data object Authenticating : ConnectionState
    data object Authenticated : ConnectionState
    data object Ready : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data class Error(val message: String) : ConnectionState
}
