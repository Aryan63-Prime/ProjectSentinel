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
    private val scope: CoroutineScope,
    private val context: android.content.Context? = null
) {

    companion object {
        private const val TAG = "Sentinel:AudioStream"
    }

    private var collectJob: Job? = null

    private val audioManager by lazy {
        context?.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
    }

    private var audioFocusRequest: Any? = null

    /** Tracks whether audio streaming was paused due to an incoming call or audio focus interruption. */
    @Volatile
    var isInterruptedByCall: Boolean = false
        private set

    val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w(TAG, "Audio focus lost transiently (call/alarm) — pausing audio capture gracefully")
                isInterruptedByCall = true
                pauseCaptureOnly()
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus regained — resuming audio capture")
                if (isInterruptedByCall) {
                    isInterruptedByCall = false
                    resumeCaptureOnly()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost permanently — stopping audio stream")
                isInterruptedByCall = false
                stop()
            }
        }
    }

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

        requestAudioFocus()
        isInterruptedByCall = false

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
        abandonAudioFocus()
        isInterruptedByCall = false
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
        abandonAudioFocus()
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

        requestAudioFocus()
        isInterruptedByCall = false

        audioRepository.startCapture()

        collectJob = pipeline.frames
            .onEach { frame -> audioRepository.sendFrame(frame) }
            .launchIn(scope)

        Log.i(TAG, "Audio streaming resumed")
    }

    private fun pauseCaptureOnly() {
        collectJob?.cancel()
        collectJob = null
        audioRepository.stopCapture()
    }

    private fun resumeCaptureOnly() {
        if (!hasPermission) return
        pauseCaptureOnly()
        audioRepository.startCapture()
        collectJob = pipeline.frames
            .onEach { frame -> audioRepository.sendFrame(frame) }
            .launchIn(scope)
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    android.media.AudioManager.STREAM_VOICE_CALL,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val req = audioFocusRequest as? android.media.AudioFocusRequest
                if (req != null) {
                    am.abandonAudioFocusRequest(req)
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    }
}
