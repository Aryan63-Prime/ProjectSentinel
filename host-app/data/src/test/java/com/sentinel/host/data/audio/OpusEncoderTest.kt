package com.sentinel.host.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the OpusEncoder interface contract.
 *
 * These tests use a [FakeOpusEncoder] since native libopus cannot
 * be loaded in JVM unit tests. The native encoding is verified
 * by the build itself (successful compilation for all ABIs).
 *
 * Integration tests that verify actual Opus encoding would run
 * as instrumented tests on a device/emulator.
 */
class OpusEncoderTest {

    @Test
    fun `encode returns positive byte count on valid input`() {
        val encoder = FakeOpusEncoder()
        val pcm = ShortArray(960) // 20ms at 48kHz
        val output = ByteArray(4000)

        val result = encoder.encode(pcm, 960, output, output.size)

        assertTrue("Encode should return positive byte count", result > 0)
    }

    @Test
    fun `encode returns correct frame size for silence`() {
        val encoder = FakeOpusEncoder()
        val pcm = ShortArray(960)
        val output = ByteArray(4000)

        val result = encoder.encode(pcm, 960, output, output.size)

        // Fake returns a 64-byte "encoded" frame
        assertEquals(64, result)
    }

    @Test
    fun `close is idempotent`() {
        val encoder = FakeOpusEncoder()
        encoder.close()
        encoder.close() // Should not throw
    }

    @Test
    fun `encode after close returns negative`() {
        val encoder = FakeOpusEncoder()
        encoder.close()

        val pcm = ShortArray(960)
        val output = ByteArray(4000)
        val result = encoder.encode(pcm, 960, output, output.size)

        assertTrue("Encode after close should return negative", result < 0)
    }

    @Test
    fun `encode with different frame sizes`() {
        val encoder = FakeOpusEncoder()
        val output = ByteArray(4000)

        // 2.5ms frame
        val result1 = encoder.encode(ShortArray(120), 120, output, output.size)
        assertTrue(result1 > 0)

        // 5ms frame
        val result2 = encoder.encode(ShortArray(240), 240, output, output.size)
        assertTrue(result2 > 0)

        // 10ms frame
        val result3 = encoder.encode(ShortArray(480), 480, output, output.size)
        assertTrue(result3 > 0)

        // 20ms frame (standard)
        val result4 = encoder.encode(ShortArray(960), 960, output, output.size)
        assertTrue(result4 > 0)
    }

    @Test
    fun `encode writes data to output buffer`() {
        val encoder = FakeOpusEncoder()
        val pcm = ShortArray(960) { 1000 } // Non-silent signal
        val output = ByteArray(4000)

        val bytesWritten = encoder.encode(pcm, 960, output, output.size)

        // Verify at least one non-zero byte in output
        val hasData = output.take(bytesWritten).any { it != 0.toByte() }
        assertTrue("Output should contain non-zero data", hasData)
    }
}

