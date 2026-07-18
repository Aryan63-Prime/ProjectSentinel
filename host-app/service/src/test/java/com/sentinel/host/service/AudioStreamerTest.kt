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
