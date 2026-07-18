package com.sentinel.host.data.repository

import com.sentinel.host.data.remote.protocol.IncomingMessage
import com.sentinel.host.data.remote.websocket.WebSocketState
import com.sentinel.host.domain.model.ConnectionEvent
import com.sentinel.host.domain.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRepositoryMappingTest {

    // ============================================================
    // WebSocketState → ConnectionState
    // ============================================================

    @Test
    fun `Disconnected maps to Disconnected`() {
        assertEquals(ConnectionState.Disconnected, WebSocketState.Disconnected.toDomainState())
    }

    @Test
    fun `Connecting maps to Connecting`() {
        assertEquals(ConnectionState.Connecting, WebSocketState.Connecting.toDomainState())
    }

    @Test
    fun `Connected maps to Connected`() {
        assertEquals(ConnectionState.Connected, WebSocketState.Connected.toDomainState())
    }

    @Test
    fun `Disconnecting maps to Disconnected`() {
        assertEquals(ConnectionState.Disconnected, WebSocketState.Disconnecting.toDomainState())
    }

    @Test
    fun `Failed maps to Error`() {
        val state = WebSocketState.Failed("timeout").toDomainState()
        assertEquals(ConnectionState.Error("timeout"), state)
    }

    // ============================================================
    // WebSocketState → ConnectionEvent
    // ============================================================

    @Test
    fun `Connected produces Connected event`() {
        assertEquals(ConnectionEvent.Connected, WebSocketState.Connected.toEvent())
    }

    @Test
    fun `Disconnected produces Disconnected event`() {
        assertEquals(ConnectionEvent.Disconnected, WebSocketState.Disconnected.toEvent())
    }

    @Test
    fun `Failed produces Error event`() {
        val event = WebSocketState.Failed("error", 500).toEvent()
        assertTrue(event is ConnectionEvent.Error)
        assertEquals(500, (event as ConnectionEvent.Error).code)
    }

    @Test
    fun `Connecting produces no event`() {
        assertNull(WebSocketState.Connecting.toEvent())
    }

    @Test
    fun `Disconnecting produces no event`() {
        assertNull(WebSocketState.Disconnecting.toEvent())
    }

    // ============================================================
    // IncomingMessage → ConnectionEvent
    // ============================================================

    @Test
    fun `AuthAck success produces Authenticated event`() {
        val msg = IncomingMessage.AuthAck("AUTH_ACK", 1, success = true)
        assertEquals(ConnectionEvent.Authenticated, msg.toEvent())
    }

    @Test
    fun `AuthAck failure produces Error event`() {
        val msg = IncomingMessage.AuthAck("AUTH_ACK", 1, success = false)
        assertTrue(msg.toEvent() is ConnectionEvent.Error)
    }

    @Test
    fun `RegisterAck success produces Registered event`() {
        val msg = IncomingMessage.RegisterAck("REGISTER_ACK", 2, success = true)
        assertEquals(ConnectionEvent.Registered, msg.toEvent())
    }

    @Test
    fun `RegisterAck failure produces Error event`() {
        val msg = IncomingMessage.RegisterAck("REGISTER_ACK", 2, success = false)
        assertTrue(msg.toEvent() is ConnectionEvent.Error)
    }

    @Test
    fun `HeartbeatAck produces HeartbeatAck event`() {
        val msg = IncomingMessage.HeartbeatAck("HEARTBEAT_ACK", 3)
        assertEquals(ConnectionEvent.HeartbeatAck, msg.toEvent())
    }

    @Test
    fun `Error produces Error event with code`() {
        val msg = IncomingMessage.Error("ERROR", 4, 403, "Forbidden")
        val event = msg.toEvent() as ConnectionEvent.Error
        assertEquals(403, event.code)
        assertEquals("Forbidden", event.message)
    }

    @Test
    fun `Pong produces null event`() {
        val msg = IncomingMessage.Pong("PONG", 5)
        assertNull(msg.toEvent())
    }

    @Test
    fun `Unknown produces null event`() {
        val msg = IncomingMessage.Unknown("FUTURE", 6)
        assertNull(msg.toEvent())
    }
}
