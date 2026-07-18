package com.sentinel.host.data.audio

import android.util.Log
import com.sentinel.host.domain.audio.AudioRecorder
import com.sentinel.host.domain.audio.OpusEncoder
import com.sentinel.host.domain.model.AudioFrame
import com.sentinel.shared.protocol.AudioConstants
import com.sentinel.shared.protocol.PacketType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single owner of the hot audio path.
 *
 * Responsibilities:
 * - Owns reusable PCM, Opus, and packet buffers (zero allocation in loop)
 * - Coordinates [AudioRecorder] and [OpusEncoder]
 * - Runs the capture→encode loop on a dedicated single-thread dispatcher
 * - Exposes encoded [AudioFrame]s via [frames] Flow
 * - Tracks statistics via [session]
 *
 * Architecture:
 * ```
 * Microphone → pcmBuffer → OpusEncoder → opusBuffer → AudioFrame → frames Flow
 *     ↑                                                                    ↓
 *  AudioRecorder                                               AudioRepository
 * ```
 *
 * Buffer design:
 * ```
 * pcmBuffer:  ShortArray(960)   — SAMPLES_PER_FRAME (20ms at 48kHz)
 * opusBuffer: ByteArray(4000)   — Max Opus frame (~4000 bytes)
 * ```
 * Both allocated once at [start], reused every 20ms cycle.
 *
 * Threading:
 * - Creates `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` on start
 * - Closes the ExecutorCoroutineDispatcher on stop (no thread leak)
 * - Each start/stop cycle gets a fresh thread
 * - Single thread guarantees AudioRecorder and OpusEncoder thread safety
 *
 * Lifecycle:
 * - [start] → creates dispatcher, allocates buffers, starts recorder, begins capture loop
 * - [stop]  → cancels loop, stops recorder, closes encoder, closes dispatcher
 */
open class AudioPipeline(
    private val recorder: AudioRecorder,
    private val encoder: OpusEncoder,
    /**
     * Test-injectable dispatcher. When non-null, this dispatcher is used
     * instead of creating a new ExecutorCoroutineDispatcher. The pipeline
     * will NOT close a test-injected dispatcher.
     */
    private val testDispatcher: CoroutineDispatcher? = null
) {

    companion object {
        private const val TAG = "Sentinel:AudioPipe"

        /** Maximum Opus encoded frame size in bytes. */
        private const val MAX_OPUS_FRAME_SIZE = 4000

        /** Break capture loop after this many consecutive read errors. */
        private const val MAX_CONSECUTIVE_ERRORS = 5
    }

    private val _frames = MutableSharedFlow<AudioFrame>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Flow of encoded audio frames. Consumers collect this to send over WebSocket. */
    val frames: SharedFlow<AudioFrame> = _frames.asSharedFlow()

    /** Audio session statistics. */
    val session = AudioSession()

    /** Audio frame sequence number. Atomic for visibility across threads. */
    private val sequenceNumber = AtomicInteger(0)

    private var captureScope: CoroutineScope? = null

    /**
     * The owned ExecutorCoroutineDispatcher — created in [start], closed in [stop].
     * Null when using a test-injected dispatcher or when stopped.
     */
    private var ownedDispatcher: ExecutorCoroutineDispatcher? = null

    /** Whether the pipeline is currently running. */
    val isRunning: Boolean get() = session.isActive

    /**
     * Starts the audio capture → encode pipeline.
     *
     * 1. Creates a dedicated audio thread dispatcher
     * 2. Initializes the encoder (if NativeOpusEncoder)
     * 3. Starts the recorder
     * 4. Begins the capture loop on the audio dispatcher
     *
     * @return true if the pipeline started successfully.
     */
    fun start(): Boolean {
        if (isRunning) {
            Log.w(TAG, "Already running — stopping first")
            stop()
        }

        // Initialize encoder if it's a NativeOpusEncoder
        if (encoder is NativeOpusEncoder) {
            if (!encoder.initialize()) {
                Log.e(TAG, "Failed to initialize Opus encoder")
                return false
            }
        }

        // Start recording
        if (!recorder.start()) {
            Log.e(TAG, "Failed to start audio recorder")
            encoder.close()
            return false
        }

        // Create dispatcher — owned by this pipeline, closed on stop
        val dispatcher: CoroutineDispatcher = testDispatcher ?: Executors.newSingleThreadExecutor { r ->
            Thread(r, "sentinel-audio").apply {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            }
        }.asCoroutineDispatcher().also { ownedDispatcher = it }

        // Reset statistics and sequence
        session.start()
        sequenceNumber.set(0)

        // Start capture loop on dedicated audio thread
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        captureScope = scope
        scope.launch { captureLoop() }

        Log.i(TAG, "Audio pipeline started")
        return true
    }

    /**
     * Stops the pipeline and releases all resources.
     * Closes the owned dispatcher to prevent thread leaks.
     */
    fun stop() {
        captureScope?.cancel()
        captureScope = null

        recorder.stop()
        encoder.close()
        session.stop()

        // Close owned dispatcher — releases the audio thread
        ownedDispatcher?.close()
        ownedDispatcher = null

        Log.i(TAG, "Audio pipeline stopped (frames=${session.framesEncoded}, " +
            "drops=${session.droppedFrames}, avgEncode=${session.averageEncodeTimeUs}µs, " +
            "p99Encode=${session.encodeTimeP99Us}µs, " +
            "maxEncode=${session.maxEncodeTimeNs / 1000}µs)")
    }

    /**
     * The hot capture loop. Runs on the audio dispatcher.
     *
     * Zero allocations per cycle:
     * - pcmBuffer and opusBuffer are allocated once before the loop
     * - AudioFrame data is copied from opusBuffer (unavoidable for Flow emission)
     */
    private suspend fun captureLoop() {
        // Pre-allocate reusable buffers
        val pcmBuffer = ShortArray(AudioConstants.SAMPLES_PER_FRAME)
        val opusBuffer = ByteArray(MAX_OPUS_FRAME_SIZE)

        Log.d(TAG, "Capture loop started (frameSize=${AudioConstants.SAMPLES_PER_FRAME}, " +
            "pcmBuffer=${pcmBuffer.size} shorts, opusBuffer=${opusBuffer.size} bytes)")

        val scope = captureScope ?: return

        var consecutiveErrors = 0

        while (scope.isActive) {
            // 1. Read PCM from microphone (blocking read — fills exactly SAMPLES_PER_FRAME)
            val samplesRead = recorder.read(pcmBuffer, 0, AudioConstants.SAMPLES_PER_FRAME)
            if (samplesRead < 0) {
                Log.e(TAG, "AudioRecord.read() error: $samplesRead")
                session.recordDrop()
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    Log.e(TAG, "Too many consecutive read errors — stopping capture")
                    break
                }
                continue
            }
            if (samplesRead < AudioConstants.SAMPLES_PER_FRAME) {
                // Partial read — shouldn't happen with blocking read, but handle it
                session.recordDrop()
                continue
            }
            consecutiveErrors = 0

            // 2. Encode PCM → Opus
            val encodeStart = System.nanoTime()
            val encodedBytes = encoder.encode(
                pcmBuffer, AudioConstants.SAMPLES_PER_FRAME,
                opusBuffer, MAX_OPUS_FRAME_SIZE
            )
            val encodeTimeNs = System.nanoTime() - encodeStart

            if (encodedBytes <= 0) {
                Log.w(TAG, "Opus encode failed: $encodedBytes")
                session.recordDrop()
                continue
            }

            // 3. Record statistics
            session.recordEncode(encodeTimeNs)

            // 4. Build AudioFrame (copy opus data — needed for safe Flow emission)
            val opusData = opusBuffer.copyOf(encodedBytes)
            val frame = AudioFrame(
                packetType = PacketType.AUDIO,
                sequence = sequenceNumber.getAndIncrement().toLong(),
                timestamp = System.currentTimeMillis(),
                opusData = opusData
            )

            // 5. Emit frame (DROP_OLDEST if consumer is slow)
            _frames.tryEmit(frame)
        }

        Log.d(TAG, "Capture loop ended")
    }
}
