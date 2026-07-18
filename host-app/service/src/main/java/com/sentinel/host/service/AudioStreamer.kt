package com.sentinel.host.service

import android.util.Log
import com.sentinel.host.data.audio.AudioPipeline
import com.sentinel.host.data.repository.AudioRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Orchestrates audio streaming tied to the connection lifecycle.
 *
 * Mirrors [LocationStreamer]'s lifecycle pattern:
 * - [start]  when ConnectionState becomes Ready
 * - [stop]   on disconnect or user stop
 * - [pause]  during reconnect — stops capture, preserves state
 * - [resume] after reconnect succeeds — restarts capture
 *
 * Permission handling:
 * - Caller must check RECORD_AUDIO permission before calling [start].
 * - If [hasPermission] is false, [start] is a no-op.
 *
 * The AudioStreamer does NOT know about ConnectionState.
 * The ConnectionSupervisor calls start/stop/pause/resume.
 *
 * Audio flow:
 * ```
 * AudioPipeline.frames → AudioRepositoryImpl.sendFrame() → WebSocket.sendBinary()
 * ```
 */
open class AudioStreamer(
    private val audioRepository: AudioRepositoryImpl,
    private val pipeline: AudioPipeline,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "Sentinel:AudioStream"
    }

    private var collectJob: Job? = null

    /** Whether RECORD_AUDIO permission has been granted. Set by the UI/permission layer. */
    @Volatile
    var hasPermission: Boolean = false

    /** Whether audio is currently being captured and streamed. */
    val isStreaming: Boolean get() = collectJob?.isActive == true && pipeline.isRunning

    /**
     * Starts audio capture and begins streaming frames.
     * No-op if [hasPermission] is false.
     */
    fun start() {
        if (!hasPermission) {
            Log.w(TAG, "No RECORD_AUDIO permission — skipping start")
            return
        }

        stop() // Prevent duplicates

        audioRepository.startCapture()

        collectJob = pipeline.frames
            .onEach { frame -> audioRepository.sendFrame(frame) }
            .launchIn(scope)

        Log.i(TAG, "Audio streaming started")
    }

    /**
     * Stops audio capture and streaming.
     */
    fun stop() {
        collectJob?.cancel()
        collectJob = null
        audioRepository.stopCapture()
        Log.i(TAG, "Audio streaming stopped")
    }

    /**
     * Pauses audio during reconnect.
     * Same as [stop] but semantically different for logging.
     */
    fun pause() {
        collectJob?.cancel()
        collectJob = null
        audioRepository.stopCapture()
        Log.i(TAG, "Audio streaming paused (reconnecting)")
    }

    /**
     * Resumes audio after reconnect.
     * Same as [start] but semantically different for logging.
     */
    fun resume() {
        if (!hasPermission) {
            Log.w(TAG, "No RECORD_AUDIO permission — skipping resume")
            return
        }

        stop() // Clean up lingering state

        audioRepository.startCapture()

        collectJob = pipeline.frames
            .onEach { frame -> audioRepository.sendFrame(frame) }
            .launchIn(scope)

        Log.i(TAG, "Audio streaming resumed")
    }
}
