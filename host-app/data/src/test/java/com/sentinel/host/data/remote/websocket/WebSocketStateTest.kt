package com.sentinel.host.data.remote.websocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketStateTest {

    @Test
    fun `Disconnected cannot send`() {
        assertFalse(WebSocketState.Disconnected.canSend())
    }

    @Test
    fun `Connecting cannot send`() {
        assertFalse(WebSocketState.Connecting.canSend())
    }

    @Test
    fun `Connected can send`() {
        assertTrue(WebSocketState.Connected.canSend())
    }

    @Test
    fun `Disconnecting cannot send`() {
        assertFalse(WebSocketState.Disconnecting.canSend())
    }

    @Test
    fun `Failed cannot send`() {
        assertFalse(WebSocketState.Failed("error").canSend())
    }

    @Test
    fun `Failed stores reason`() {
        val state = WebSocketState.Failed("timeout", code = 408)
        assertEquals("timeout", state.reason)
        assertEquals(408, state.code)
    }

    @Test
    fun `Failed with null code`() {
        val state = WebSocketState.Failed("network error")
        assertEquals("network error", state.reason)
        assertEquals(null, state.code)
    }

    @Test
    fun `all states are distinct`() {
        val states = listOf(
            WebSocketState.Disconnected,
            WebSocketState.Connecting,
            WebSocketState.Connected,
            WebSocketState.Disconnecting,
            WebSocketState.Failed("err")
        )
        assertEquals(5, states.toSet().size)
    }
}
