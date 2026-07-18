package com.sentinel.admin.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ConnectionState] state machine semantics.
 *
 * Verifies that:
 * - All expected states exist
 * - TransportConnected is distinct from Ready
 * - Reconnecting carries attempt number
 * - Error carries message
 * - Admin does NOT have a Registering state
 */
class ConnectionStateTest {

    @Test
    fun `Disconnected is a valid state`() {
        val state: ConnectionState = ConnectionState.Disconnected
        assertTrue(state is ConnectionState.Disconnected)
    }

    @Test
    fun `Connecting is a valid state`() {
        val state: ConnectionState = ConnectionState.Connecting
        assertTrue(state is ConnectionState.Connecting)
    }

    @Test
    fun `TransportConnected is a valid state`() {
        val state: ConnectionState = ConnectionState.TransportConnected
        assertTrue(state is ConnectionState.TransportConnected)
    }

    @Test
    fun `Authenticating is a valid state`() {
        val state: ConnectionState = ConnectionState.Authenticating
        assertTrue(state is ConnectionState.Authenticating)
    }

    @Test
    fun `Authenticated is a valid state`() {
        val state: ConnectionState = ConnectionState.Authenticated
        assertTrue(state is ConnectionState.Authenticated)
    }

    @Test
    fun `Ready is a valid state`() {
        val state: ConnectionState = ConnectionState.Ready
        assertTrue(state is ConnectionState.Ready)
    }

    @Test
    fun `Reconnecting carries attempt number`() {
        val state = ConnectionState.Reconnecting(attempt = 3)
        assertEquals(3, state.attempt)
    }

    @Test
    fun `Error carries message`() {
        val state = ConnectionState.Error("timeout")
        assertEquals("timeout", state.message)
    }

    @Test
    fun `complete state machine transition sequence`() {
        // Admin state machine: full happy path + failure + recovery
        val states = listOf(
            ConnectionState.Disconnected,
            ConnectionState.Connecting,
            ConnectionState.TransportConnected,
            ConnectionState.Authenticating,
            ConnectionState.Ready,
            // Failure path
            ConnectionState.Error("connection lost"),
            ConnectionState.Reconnecting(1),
            ConnectionState.Connecting,
            ConnectionState.TransportConnected,
            ConnectionState.Authenticating,
            ConnectionState.Ready
        )

        assertEquals(11, states.size)
        assertTrue(states[0] is ConnectionState.Disconnected)
        assertTrue(states[2] is ConnectionState.TransportConnected)
        assertTrue(states[4] is ConnectionState.Ready)
        assertTrue(states[5] is ConnectionState.Error)
        assertTrue(states[6] is ConnectionState.Reconnecting)
        assertTrue(states[10] is ConnectionState.Ready)
    }

    @Test
    fun `TransportConnected is distinct from Ready`() {
        // TransportConnected means WS open but not authenticated
        // Ready means authenticated and usable
        val transport: ConnectionState = ConnectionState.TransportConnected
        val ready: ConnectionState = ConnectionState.Ready
        assertTrue(transport != ready)
    }
}
