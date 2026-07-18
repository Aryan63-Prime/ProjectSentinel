package com.sentinel.admin.data.remote.websocket

/**
 * Transport-level WebSocket connection state.
 * Distinct from domain ConnectionState which includes
 * business-level states (Authenticating, Ready).
 */
sealed interface WebSocketState {
    data object Disconnected : WebSocketState
    data object Connecting : WebSocketState
    data object Connected : WebSocketState
    data object Disconnecting : WebSocketState
    data class Failed(val reason: String, val code: Int? = null) : WebSocketState

    /** Returns true if a send operation is valid in this state. */
    fun canSend(): Boolean = this is Connected
}
