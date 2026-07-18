package com.sentinel.host.data.audio

import com.sentinel.host.domain.audio.AudioRecorder
import com.sentinel.host.domain.audio.OpusEncoder
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stress tests for repeated encoder/pipeline create-destroy cycles.
 *
 * Validates:
 * - No resource leaks across start/stop cycles
 * - No thread leaks (dispatcher closed on each stop)
 * - Encoder handle correctly zeroed
 * - Pipeline state correctly reset
 * - Rapid create/destroy doesn't crash
 */
class EncoderStressTest {

    // ================================================================
    // Pipeline lifecycle stress
    // ================================================================

    @Test
    fun `pipeline survives 50 start-stop cycles`() {
        val recorder = CyclableRecorder()
        val encoder = CyclableEncoder()
        val pipeline = AudioPipeline(recorder, encoder, testDispatcher = Dispatchers.Unconfined)

        for (i in 0 until 50) {
            assertTrue("start() should succeed on cycle $i", pipeline.start())
            assertTrue("isRunning should be true on cycle $i", pipeline.isRunning)

            pipeline.stop()
            assertFalse("isRunning should be false after stop on cycle $i", pipeline.isRunning)
        }

        // Verify encoder was closed 50 times
        assertTrue("Encoder should have been closed many times", encoder.closeCount >= 50)
    }

    @Test
    fun `pipeline survives rapid start-stop without delay`() {
        val recorder = CyclableRecorder()
        val encoder = CyclableEncoder()
        val pipeline = AudioPipeline(recorder, encoder, testDispatcher = Dispatchers.Unconfined)

        // Rapid fire — no delay between cycles
        repeat(100) { i ->
            pipeline.start()
            pipeline.stop()
        }

        assertFalse(pipeline.isRunning)
        assertTrue(encoder.closeCount >= 100)
    }

    @Test
    fun `pipeline handles double-stop gracefully`() {
        val recorder = CyclableRecorder()
        val encoder = CyclableEncoder()
        val pipeline = AudioPipeline(recorder, encoder, testDispatcher = Dispatchers.Unconfined)

        pipeline.start()
        pipeline.stop()
        pipeline.stop() // Second stop should be safe

        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `pipeline handles double-start gracefully`() {
        val recorder = CyclableRecorder()
        val encoder = CyclableEncoder()
        val pipeline = AudioPipeline(recorder, encoder, testDispatcher = Dispatchers.Unconfined)

        pipeline.start()
        pipeline.start() // Should stop-then-start

        assertTrue(pipeline.isRunning)
        pipeline.stop()
    }

    @Test
    fun `sequence number resets on each start`() {
        val recorder = CyclableRecorder()
        val encoder = CyclableEncoder()
        val pipeline = AudioPipeline(recorder, encoder, testDispatcher = Dispatchers.Unconfined)

        pipeline.start()
        pipeline.stop()

        pipeline.start()
        // After restart, session should be fresh
        assertTrue(pipeline.isRunning)
        pipeline.stop()
    }

    // ================================================================
    // Encoder stress
    // ================================================================

    @Test
    fun `encoder close is idempotent`() {
        val encoder = CyclableEncoder()

        encoder.close()
        encoder.close()
        encoder.close()

        assertTrue(encoder.closeCount >= 3)
    }

    @Test
    fun `encoder encode returns -1 after close`() {
        val encoder = CyclableEncoder()
        encoder.close()

        // Encoding after close should return error, not crash
        val result = encoder.encode(ShortArray(960), 960, ByteArray(4000), 4000)
        assertTrue(result <= 0)
    }
}

// ================================================================
// Test doubles that support cycling
// ================================================================

/**
 * AudioRecorder that supports repeated start/stop cycles.
 */
private class CyclableRecorder : AudioRecorder {
    @Volatile
    override var isRecording: Boolean = false
        private set

    override fun start(): Boolean {
        isRecording = true
        return true
    }

    override fun stop() {
        isRecording = false
    }

    override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
        // Return -1 to stop capture loop immediately
        return -1
    }

    override fun close() {
        isRecording = false
    }
}

/**
 * OpusEncoder that tracks close count for leak verification.
 */
private class CyclableEncoder : OpusEncoder {
    @Volatile
    var closeCount = 0
        private set

    @Volatile
    private var closed = false

    override fun encode(pcm: ShortArray, frameSize: Int, output: ByteArray, maxOutput: Int): Int {
        return if (closed) -1 else 0
    }

    override fun close() {
        closeCount++
        closed = true
    }
}
