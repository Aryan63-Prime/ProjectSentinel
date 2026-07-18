package com.sentinel.admin.domain.repository

import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.ConnectionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Observes and controls the WebSocket connection lifecycle.
 *
 * Emits [ConnectionEvent]s from deserialized incoming messages
 * and binary audio frames.
 */
interface ConnectionRepository {
    /** Current connection state. */
    val state: StateFlow<ConnectionState>

    /** Events from server (auth results, heartbeat acks, audio frames, errors). */
    val events: SharedFlow<ConnectionEvent>

    /** Connects to the server WebSocket endpoint. */
    suspend fun connect(serverUrl: String)

    /** Disconnects from the server. */
    suspend fun disconnect()

    /** Sends a JSON control message. Returns true if sent successfully. */
    fun sendText(message: String): Boolean

    /** Sends a binary message. Returns true if sent successfully. */
    fun sendBinary(data: ByteArray): Boolean
}
