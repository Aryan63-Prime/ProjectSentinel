package com.sentinel.host.service

import com.sentinel.host.data.audio.AudioPipeline
import com.sentinel.host.data.repository.AudioRepositoryImpl
import com.sentinel.host.domain.audio.AudioRecorder
import com.sentinel.host.domain.audio.OpusEncoder
import com.sentinel.host.domain.model.ConnectionEvent
import com.sentinel.host.domain.model.ConnectionState
import com.sentinel.host.domain.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioStreamerTest {

    private lateinit var streamer: AudioStreamer
    private lateinit var pipeline: AudioPipeline
    private lateinit var audioRepo: AudioRepositoryImpl

    @Before
    fun setUp() {
        val connection = object : ConnectionRepository {
            override val state = MutableStateFlow(ConnectionState.Disconnected)
            override val events: SharedFlow<ConnectionEvent> = MutableSharedFlow(extraBufferCapacity = 64)
            override suspend fun connect(serverUrl: String) {}
            override suspend fun disconnect() {}
            override fun sendText(message: String) = true
            override fun sendBinary(data: ByteArray) = true
        }

        pipeline = AudioPipeline(
            recorder = NoopRecorder(),
            encoder = NoopEncoder(),
            testDispatcher = Dispatchers.Unconfined
        )

        audioRepo = AudioRepositoryImpl(pipeline, connection)

        streamer = AudioStreamer(
            audioRepository = audioRepo,
            pipeline = pipeline,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    // ================================================================
    // Permission gating
    // ================================================================

    @Test
    fun `start without permission is no-op`() {
        streamer.hasPermission = false
        streamer.start()
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `start with permission starts pipeline`() {
        streamer.hasPermission = true
        streamer.start()
        assertTrue(pipeline.isRunning)
    }

    @Test
    fun `resume without permission is no-op`() {
        streamer.hasPermission = false
        streamer.resume()
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `resume with permission starts pipeline`() {
        streamer.hasPermission = true
        streamer.resume()
        assertTrue(pipeline.isRunning)
    }

    // ================================================================
    // Stop / Pause
    // ================================================================

    @Test
    fun `stop stops pipeline`() {
        streamer.hasPermission = true
        streamer.start()
        assertTrue(pipeline.isRunning)

        streamer.stop()
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `pause stops pipeline`() {
        streamer.hasPermission = true
        streamer.start()
        assertTrue(pipeline.isRunning)

        streamer.pause()
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `stop is safe when not started`() {
        streamer.stop() // Should not throw
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `pause is safe when not started`() {
        streamer.pause() // Should not throw
        assertFalse(pipeline.isRunning)
    }

    // ================================================================
    // Idempotency
    // ================================================================

    @Test
    fun `double start is idempotent`() {
        streamer.hasPermission = true
        streamer.start()
        streamer.start()
        assertTrue(pipeline.isRunning)
    }

    @Test
    fun `double stop is idempotent`() {
        streamer.hasPermission = true
        streamer.start()
        streamer.stop()
        streamer.stop()
        assertFalse(pipeline.isRunning)
    }

    // ================================================================
    // Lifecycle cycle
    // ================================================================

    @Test
    fun `start stop start cycle works`() {
        streamer.hasPermission = true

        streamer.start()
        assertTrue(pipeline.isRunning)

        streamer.stop()
        assertFalse(pipeline.isRunning)

        streamer.start()
        assertTrue(pipeline.isRunning)

        streamer.stop()
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `start pause resume stop cycle works`() {
        streamer.hasPermission = true

        streamer.start()
        assertTrue(pipeline.isRunning)

        streamer.pause()
        assertFalse(pipeline.isRunning)

        streamer.resume()
        assertTrue(pipeline.isRunning)

        streamer.stop()
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `audio focus transient loss pauses streaming and gain resumes it`() {
        streamer.hasPermission = true
        streamer.start()
        assertTrue(pipeline.isRunning)

        // Simulate incoming call / transient focus loss
        streamer.audioFocusChangeListener.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue(streamer.isInterruptedByCall)
        assertFalse(pipeline.isRunning)

        // Call ends / audio focus gained back
        streamer.audioFocusChangeListener.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(streamer.isInterruptedByCall)
        assertTrue(pipeline.isRunning)

        streamer.stop()
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `audio focus permanent loss stops streaming completely`() {
        streamer.hasPermission = true
        streamer.start()
        assertTrue(pipeline.isRunning)

        // Permanent loss
        streamer.audioFocusChangeListener.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS)
        assertFalse(streamer.isInterruptedByCall)
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `session timeout stops streaming and invokes callback`() = runTest {
        val testScope = this
        val timedStreamer = AudioStreamer(
            audioRepository = audioRepo,
            pipeline = pipeline,
            scope = testScope
        )
        var timedOut = false
        timedStreamer.onSessionTimeout = { timedOut = true }
        timedStreamer.hasPermission = true
        timedStreamer.start(maxDurationSeconds = 1L)
        assertTrue(pipeline.isRunning)

        testScheduler.advanceTimeBy(1500L)
        advanceUntilIdle()

        assertFalse(pipeline.isRunning)
        assertTrue(timedOut)
    }

    @Test
    fun `lifecycle callbacks are invoked on start and stop`() {
        var started = false
        var stopped = false
        streamer.onSessionStarted = { started = true }
        streamer.onSessionStopped = { stopped = true }
        streamer.hasPermission = true

        streamer.start()
        assertTrue(started)

        streamer.stop()
        assertTrue(stopped)
    }
}

// ================================================================
// Test doubles
// ================================================================

private class NoopRecorder : AudioRecorder {
    override val isRecording = false
    override fun start() = true
    override fun stop() {}
    override fun read(buffer: ShortArray, offset: Int, size: Int) = -1
    override fun close() {}
}

private class NoopEncoder : OpusEncoder {
    override fun encode(pcm: ShortArray, frameSize: Int, output: ByteArray, maxOutput: Int) = -1
    override fun close() {}
}
