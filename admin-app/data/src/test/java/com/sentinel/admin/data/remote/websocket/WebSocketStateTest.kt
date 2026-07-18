package com.sentinel.admin.data.remote.websocket

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WebSocketState].
 */
class WebSocketStateTest {

    @Test
    fun `canSend returns true only for Connected`() {
        assertTrue(WebSocketState.Connected.canSend())
    }

    @Test
    fun `canSend returns false for Disconnected`() {
        assertFalse(WebSocketState.Disconnected.canSend())
    }

    @Test
    fun `canSend returns false for Connecting`() {
        assertFalse(WebSocketState.Connecting.canSend())
    }

    @Test
    fun `canSend returns false for Disconnecting`() {
        assertFalse(WebSocketState.Disconnecting.canSend())
    }

    @Test
    fun `canSend returns false for Failed`() {
        assertFalse(WebSocketState.Failed("error").canSend())
    }

    @Test
    fun `Failed stores reason and code`() {
        val state = WebSocketState.Failed("timeout", 408)
        assertTrue(state is WebSocketState.Failed)
        val failed = state as WebSocketState.Failed
        assertTrue("timeout" == failed.reason)
        assertTrue(408 == failed.code)
    }

    @Test
    fun `Failed with null code`() {
        val state = WebSocketState.Failed("no code")
        assertTrue(state.code == null)
    }
}
