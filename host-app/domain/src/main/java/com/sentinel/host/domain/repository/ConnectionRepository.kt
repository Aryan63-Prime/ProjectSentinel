package com.sentinel.host.domain.repository

import com.sentinel.host.domain.model.ConnectionEvent
import com.sentinel.host.domain.model.ConnectionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Observes and controls the WebSocket connection lifecycle.
 * Emits [ConnectionEvent]s from deserialized incoming messages.
 */
interface ConnectionRepository {
    val state: StateFlow<ConnectionState>
    val events: SharedFlow<ConnectionEvent>
    suspend fun connect(serverUrl: String)
    suspend fun disconnect()
    fun sendText(message: String): Boolean
    fun sendBinary(data: ByteArray): Boolean
}

