package com.sentinel.host.service

import android.util.Log
import com.sentinel.host.data.audio.AudioPipeline
import com.sentinel.host.data.repository.AudioRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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

    /** Default maximum duration for an on-demand listening session (5 minutes). */
    val defaultMaxDurationSeconds: Long = 300L

    private var timeoutJob: Job? = null

    /** Callbacks invoked when audio streaming session starts, stops, or times out. */
    var onSessionStarted: (() -> Unit)? = null
    var onSessionStopped: (() -> Unit)? = null
    var onSessionTimeout: (() -> Unit)? = null

    /** Whether RECORD_AUDIO permission has been granted. Set by the UI/permission layer. */
    @Volatile
    var hasPermission: Boolean = false

    private val lifecycleLock = Any()

    /** Whether audio is currently being captured and streamed. */
    val isStreaming: Boolean get() = collectJob?.isActive == true && pipeline.isRunning

    /**
     * Starts audio capture and begins streaming frames.
     * No-op if [hasPermission] is false.
     * If already streaming, extends the session timeout without tearing down the pipeline.
     * Automatically stops and releases mic if [maxDurationSeconds] expires.
     */
    fun start(maxDurationSeconds: Long = defaultMaxDurationSeconds): Unit = synchronized(lifecycleLock) {
        if (!hasPermission) {
            Log.w(TAG, "No RECORD_AUDIO permission — skipping start")
            return
        }

        if (isStreaming) {
            Log.i(TAG, "Audio streaming already active — extending session timeout")
            if (maxDurationSeconds > 0) {
                timeoutJob?.cancel()
                timeoutJob = scope.launch {
                    kotlinx.coroutines.delay(maxDurationSeconds * 1000L)
                    Log.i(TAG, "Audio session reached maximum duration ($maxDurationSeconds s) — auto-stopping mic")
                    stop()
                    onSessionTimeout?.invoke()
                }
            }
            return
        }

        stopInternal() // Prevent lingering state

        requestAudioFocus()
        isInterruptedByCall = false

        audioRepository.startCapture()

        collectJob = pipeline.frames
            .onEach { frame -> audioRepository.sendFrame(frame) }
            .launchIn(scope)

        if (maxDurationSeconds > 0) {
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                kotlinx.coroutines.delay(maxDurationSeconds * 1000L)
                Log.i(TAG, "Audio session reached maximum duration ($maxDurationSeconds s) — auto-stopping mic")
                stop()
                onSessionTimeout?.invoke()
            }
        }

        onSessionStarted?.invoke()
        Log.i(TAG, "Audio streaming started (maxDuration=${maxDurationSeconds}s)")
    }

    /**
     * Stops audio capture and streaming, releases hardware resources.
     */
    fun stop(): Unit = synchronized(lifecycleLock) {
        stopInternal()
    }

    private fun stopInternal() {
        timeoutJob?.cancel()
        timeoutJob = null
        abandonAudioFocus()
        isInterruptedByCall = false
        collectJob?.cancel()
        collectJob = null
        audioRepository.stopCapture()
        onSessionStopped?.invoke()
        Log.i(TAG, "Audio streaming stopped")
    }

    /**
     * Pauses audio during reconnect.
     * Releases hardware resources during connection loss.
     */
    fun pause(): Unit = synchronized(lifecycleLock) {
        timeoutJob?.cancel()
        timeoutJob = null
        abandonAudioFocus()
        collectJob?.cancel()
        collectJob = null
        audioRepository.stopCapture()
        onSessionStopped?.invoke()
        Log.i(TAG, "Audio streaming paused (reconnecting)")
    }

    /**
     * Resumes audio after reconnect.
     * Same as [start] but semantically different for logging.
     */
    fun resume(): Unit = synchronized(lifecycleLock) {
        if (!hasPermission) {
            Log.w(TAG, "No RECORD_AUDIO permission — skipping resume")
            return
        }

        stopInternal() // Clean up lingering state

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
