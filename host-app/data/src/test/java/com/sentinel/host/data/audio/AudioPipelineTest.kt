package com.sentinel.host.data.audio

import com.sentinel.host.domain.audio.AudioRecorder
import com.sentinel.host.domain.audio.OpusEncoder
import com.sentinel.shared.protocol.PacketType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPipelineTest {

    // ================================================================
    // Lifecycle
    // ================================================================

    @Test
    fun `start initializes and begins capture`() = runTest {
        val recorder = FakeAudioRecorder(framesToProvide = 3)
        val encoder = FakeOpusEncoder()
        val pipeline = AudioPipeline(recorder, encoder, UnconfinedTestDispatcher(testScheduler))

        val started = pipeline.start()

        assertTrue(started)
        assertTrue(pipeline.isRunning)
        // Recorder was started (may have finished by now due to limited frames)
        assertTrue(recorder.startCount > 0)

        pipeline.stop()
    }

    @Test
    fun `stop cleans up all resources`() = runTest {
        val recorder = FakeAudioRecorder(framesToProvide = 3)
        val encoder = FakeOpusEncoder()
        val pipeline = AudioPipeline(recorder, encoder, UnconfinedTestDispatcher(testScheduler))

        pipeline.start()
        pipeline.stop()

        assertFalse(pipeline.isRunning)
        assertFalse(recorder.isRecording)
        assertTrue(encoder.isClosed)
    }

    @Test
    fun `start is idempotent - stops previous before starting new`() = runTest {
        val recorder = FakeAudioRecorder(framesToProvide = 3)
        val encoder = FakeOpusEncoder()
        val pipeline = AudioPipeline(recorder, encoder, UnconfinedTestDispatcher(testScheduler))

        pipeline.start()
        pipeline.start() // Should stop previous first

        assertTrue(pipeline.isRunning)
        assertEquals(2, recorder.startCount)

        pipeline.stop()
    }

    @Test
    fun `stop is idempotent`() = runTest {
        val recorder = FakeAudioRecorder(framesToProvide = 1)
        val encoder = FakeOpusEncoder()
        val pipeline = AudioPipeline(recorder, encoder, UnconfinedTestDispatcher(testScheduler))

        pipeline.stop()
        pipeline.stop() // Should not throw

        assertFalse(pipeline.isRunning)
    }

    // ================================================================
    // Recorder failure
    // ================================================================

    @Test
    fun `start fails if recorder fails to start`() = runTest {
        val recorder = FakeAudioRecorder(framesToProvide = 0).apply { shouldFailStart = true }
        val encoder = FakeOpusEncoder()
        val pipeline = AudioPipeline(recorder, encoder, UnconfinedTestDispatcher(testScheduler))

        val started = pipeline.start()

        assertFalse(started)
        assertFalse(pipeline.isRunning)
        assertTrue(encoder.isClosed) // Encoder should be cleaned up
    }

    // ================================================================
    // Frame emission
    // ================================================================

    @Test
    fun `pipeline emits frames from capture loop`() = runBlocking {
        val recorder = FakeAudioRecorder(framesToProvide = 5)
        val encoder = FakeOpusEncoder()
        val pipeline = AudioPipeline(recorder, encoder, Dispatchers.Default)

        // Subscribe BEFORE start (replay=0 — no stale frames)
        val frameDeferred = async { pipeline.frames.first() }
        delay(50) // Ensure subscription is active

        pipeline.start()

        val frame = withTimeout(2000) { frameDeferred.await() }

        assertEquals(PacketType.AUDIO, frame.packetType)
        assertTrue(frame.sequence >= 0)
        assertTrue(frame.timestamp > 0)
        assertTrue(frame.opusData.isNotEmpty())

        pipeline.stop()
    }

    // ================================================================
    // Sequence numbers
    // ================================================================

    @Test
    fun `frames have non-negative sequence numbers`() = runBlocking {
        val recorder = FakeAudioRecorder(framesToProvide = 3)
        val encoder = FakeOpusEncoder()
        val pipeline = AudioPipeline(recorder, encoder, Dispatchers.Default)

        // Subscribe BEFORE start
        val frameDeferred = async { pipeline.frames.first() }
        delay(50)

        pipeline.start()

        val frame = withTimeout(2000) { frameDeferred.await() }
        assertTrue(frame.sequence >= 0)

        pipeline.stop()
    }

    // ================================================================
    // Statistics
    // ================================================================

    @Test
    fun `session tracks frames after encoding`() = runTest {
        val recorder = FakeAudioRecorder(framesToProvide = 5)
        val encoder = FakeOpusEncoder()
        val pipeline = AudioPipeline(recorder, encoder, UnconfinedTestDispatcher(testScheduler))

        pipeline.start()
        advanceUntilIdle()

        assertTrue("Should have encoded frames", pipeline.session.framesEncoded > 0)

        pipeline.stop()
    }

    @Test
    fun `session tracks drops on encode failure`() = runTest {
        val recorder = FakeAudioRecorder(framesToProvide = 3)
        val encoder = FakeOpusEncoder().apply { shouldFailEncode = true }
        val pipeline = AudioPipeline(recorder, encoder, UnconfinedTestDispatcher(testScheduler))

        pipeline.start()
        advanceUntilIdle()

        assertTrue("Should have dropped frames", pipeline.session.droppedFrames > 0)

        pipeline.stop()
    }

    // ================================================================
    // Resource cleanup
    // ================================================================

    @Test
    fun `encoder is closed on stop`() = runTest {
        val encoder = FakeOpusEncoder()
        val recorder = FakeAudioRecorder(framesToProvide = 2)
        val pipeline = AudioPipeline(recorder, encoder, UnconfinedTestDispatcher(testScheduler))

        pipeline.start()
        pipeline.stop()

        assertTrue(encoder.isClosed)
    }

    @Test
    fun `recorder is stopped on stop`() = runTest {
        val recorder = FakeAudioRecorder(framesToProvide = 2)
        val pipeline = AudioPipeline(recorder, FakeOpusEncoder(), UnconfinedTestDispatcher(testScheduler))

        pipeline.start()
        pipeline.stop()

        assertFalse(recorder.isRecording)
    }
}

// ================================================================
// Test fakes
// ================================================================

internal class FakeAudioRecorder(
    private val framesToProvide: Int = Int.MAX_VALUE
) : AudioRecorder {

    @Volatile
    override var isRecording: Boolean = false
        private set

    var shouldFailStart = false
    var startCount = 0
        private set
    private var framesRead = 0

    override fun start(): Boolean {
        if (shouldFailStart) return false
        isRecording = true
        startCount++
        framesRead = 0
        return true
    }

    override fun stop() {
        isRecording = false
    }

    override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
        if (!isRecording || framesRead >= framesToProvide) {
            isRecording = false
            return -1
        }
        // Fill with fake PCM data
        for (i in offset until minOf(offset + size, buffer.size)) {
            buffer[i] = ((i * 100) % Short.MAX_VALUE).toShort()
        }
        framesRead++
        return size
    }

    override fun close() {
        stop()
    }
}

internal class FakeOpusEncoder : OpusEncoder {

    var isClosed = false
        private set
    var shouldFailEncode = false

    override fun encode(pcm: ShortArray, frameSize: Int, output: ByteArray, maxOutput: Int): Int {
        if (isClosed) return -1
        if (shouldFailEncode) return -1

        // Produce a fake 64-byte Opus frame
        val outSize = minOf(64, maxOutput)
        for (i in 0 until outSize) {
            output[i] = (pcm[i % pcm.size].toInt() xor i).toByte()
        }
        return outSize
    }

    override fun close() {
        isClosed = true
    }
}
