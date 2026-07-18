package com.sentinel.admin.data.audio

import com.sentinel.shared.protocol.AudioConstants
import com.sentinel.shared.protocol.PacketType
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests for [AudioFrameParser].
 *
 * Verifies header validation, field extraction, and rejection of invalid frames.
 */
class AudioFrameParserTest {

    // ============================================================
    // Valid frame parsing
    // ============================================================

    @Test
    fun `parse valid frame extracts all fields`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val frame = buildFrame(
            packetType = PacketType.AUDIO,
            sequence = 42,
            timestamp = 1000L,
            payload = payload
        )

        val result = AudioFrameParser.parse(frame)

        assertNotNull(result)
        assertEquals(PacketType.AUDIO, result!!.packetType)
        assertEquals(42L, result.sequence)
        assertEquals(1000L, result.timestamp)
        assertArrayEquals(payload, result.payload)
    }

    @Test
    fun `parse frame with sequence zero`() {
        val frame = buildFrame(PacketType.AUDIO, 0, 500L, byteArrayOf(0x10))
        val result = AudioFrameParser.parse(frame)

        assertNotNull(result)
        assertEquals(0L, result!!.sequence)
    }

    @Test
    fun `parse frame with max uint32 sequence`() {
        // 0xFFFFFFFF = 4294967295
        val frame = buildFrame(PacketType.AUDIO, 0xFFFFFFFFL, 500L, byteArrayOf(0x10))
        val result = AudioFrameParser.parse(frame)

        assertNotNull(result)
        assertEquals(0xFFFFFFFFL, result!!.sequence)
    }

    @Test
    fun `parse frame with large timestamp`() {
        val ts = System.currentTimeMillis()
        val frame = buildFrame(PacketType.AUDIO, 1, ts, byteArrayOf(0x10))
        val result = AudioFrameParser.parse(frame)

        assertNotNull(result)
        assertEquals(ts, result!!.timestamp)
    }

    @Test
    fun `parse frame with large payload`() {
        val payload = ByteArray(500) { it.toByte() }
        val frame = buildFrame(PacketType.AUDIO, 99, 0L, payload)
        val result = AudioFrameParser.parse(frame)

        assertNotNull(result)
        assertEquals(500, result!!.payload.size)
        assertArrayEquals(payload, result.payload)
    }

    // ============================================================
    // Rejection
    // ============================================================

    @Test
    fun `parse rejects frame shorter than header`() {
        val tooShort = ByteArray(AudioConstants.HEADER_SIZE - 1)
        assertNull(AudioFrameParser.parse(tooShort))
    }

    @Test
    fun `parse rejects empty byte array`() {
        assertNull(AudioFrameParser.parse(byteArrayOf()))
    }

    @Test
    fun `parse rejects wrong packet type`() {
        val frame = buildFrame(0x02.toByte(), 1, 100L, byteArrayOf(0x10))
        assertNull(AudioFrameParser.parse(frame))
    }

    @Test
    fun `parse rejects header-only frame with no payload`() {
        // Exactly HEADER_SIZE bytes, no payload
        val headerOnly = ByteBuffer.allocate(AudioConstants.HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .put(PacketType.AUDIO)
            .putInt(1)
            .putLong(100L)
            .array()

        assertNull(AudioFrameParser.parse(headerOnly))
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun buildFrame(
        packetType: Byte,
        sequence: Long,
        timestamp: Long,
        payload: ByteArray
    ): ByteArray {
        val buffer = ByteBuffer.allocate(AudioConstants.HEADER_SIZE + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(packetType)
        buffer.putInt(sequence.toInt())
        buffer.putLong(timestamp)
        buffer.put(payload)
        return buffer.array()
    }
}
