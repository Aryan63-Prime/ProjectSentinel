package com.sentinel.admin.data.repository

import com.sentinel.admin.data.remote.protocol.IncomingMessage
import com.sentinel.admin.data.remote.websocket.WebSocketState
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ConnectionRepositoryImpl mapping functions.
 *
 * Covers:
 * - WebSocketState → ConnectionState mapping
 * - WebSocketState → ConnectionEvent mapping
 * - IncomingMessage → ConnectionEvent mapping
 */
class ConnectionMappingTest {

    // ============================================================
    // WebSocketState → ConnectionState
    // ============================================================

    @Test
    fun `Disconnected maps to Disconnected`() {
        val result = WebSocketState.Disconnected.toDomainState()
        assertEquals(ConnectionState.Disconnected, result)
    }

    @Test
    fun `Connecting maps to Connecting`() {
        val result = WebSocketState.Connecting.toDomainState()
        assertEquals(ConnectionState.Connecting, result)
    }

    @Test
    fun `Connected maps to TransportConnected`() {
        val result = WebSocketState.Connected.toDomainState()
        assertEquals(ConnectionState.TransportConnected, result)
    }

    @Test
    fun `Disconnecting maps to Disconnected`() {
        val result = WebSocketState.Disconnecting.toDomainState()
        assertEquals(ConnectionState.Disconnected, result)
    }

    @Test
    fun `Failed maps to Error with reason`() {
        val result = WebSocketState.Failed("timeout", 408).toDomainState()
        assertTrue(result is ConnectionState.Error)
        assertEquals("timeout", (result as ConnectionState.Error).message)
    }

    // ============================================================
    // WebSocketState → ConnectionEvent
    // ============================================================

    @Test
    fun `Connected state emits Connected event`() {
        val event = WebSocketState.Connected.toEvent()
        assertEquals(ConnectionEvent.Connected, event)
    }

    @Test
    fun `Disconnected state emits Disconnected event`() {
        val event = WebSocketState.Disconnected.toEvent()
        assertEquals(ConnectionEvent.Disconnected, event)
    }

    @Test
    fun `Failed state emits ServerError event`() {
        val event = WebSocketState.Failed("network error", 500).toEvent()
        assertTrue(event is ConnectionEvent.ServerError)
        val error = event as ConnectionEvent.ServerError
        assertEquals(500, error.code)
        assertEquals("network error", error.message)
    }

    @Test
    fun `Failed state without code uses 0`() {
        val event = WebSocketState.Failed("timeout").toEvent()
        assertTrue(event is ConnectionEvent.ServerError)
        assertEquals(0, (event as ConnectionEvent.ServerError).code)
    }

    @Test
    fun `Connecting state emits null event`() {
        assertNull(WebSocketState.Connecting.toEvent())
    }

    @Test
    fun `Disconnecting state emits null event`() {
        assertNull(WebSocketState.Disconnecting.toEvent())
    }

    // ============================================================
    // IncomingMessage → ConnectionEvent
    // ============================================================

    @Test
    fun `AuthAck success maps to AuthResult success`() {
        val msg = IncomingMessage.AuthAck("AUTH_ACK", 1, true)
        val event = msg.toEvent()

        assertTrue(event is ConnectionEvent.AuthResult)
        assertTrue((event as ConnectionEvent.AuthResult).success)
    }

    @Test
    fun `AuthAck failure maps to AuthResult failure`() {
        val msg = IncomingMessage.AuthAck("AUTH_ACK", 1, false)
        val event = msg.toEvent()

        assertTrue(event is ConnectionEvent.AuthResult)
        assertTrue(!(event as ConnectionEvent.AuthResult).success)
    }

    @Test
    fun `HeartbeatAck maps to HeartbeatAck event`() {
        val msg = IncomingMessage.HeartbeatAck("HEARTBEAT_ACK", 5)
        val event = msg.toEvent()

        assertEquals(ConnectionEvent.HeartbeatAck, event)
    }

    @Test
    fun `Error maps to ServerError event`() {
        val msg = IncomingMessage.Error("ERROR", 3, 403, "Forbidden")
        val event = msg.toEvent()

        assertTrue(event is ConnectionEvent.ServerError)
        val error = event as ConnectionEvent.ServerError
        assertEquals(403, error.code)
        assertEquals("Forbidden", error.message)
    }

    @Test
    fun `Pong maps to null event`() {
        val msg = IncomingMessage.Pong("PONG", 7)
        assertNull(msg.toEvent())
    }

    @Test
    fun `Unknown maps to null event`() {
        val msg = IncomingMessage.Unknown("FOOBAR", 9)
        assertNull(msg.toEvent())
    }
}
