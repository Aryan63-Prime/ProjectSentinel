package com.sentinel.host.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance, crash-proof PTT Audio Player for Sentinel Host.
 *
 * Concurrency & Stability Architecture:
 * - Employs a dedicated single-thread executor to confine all [AudioTrack] state transitions
 *   (initialization, write, stop, release) to a single OS thread.
 * - This completely eradicates the native Android SIGSEGV null pointer dereference inside
 *   android::AudioTrack::releaseBuffer that occurs when audioTrack.release() is invoked
 *   concurrently with audioTrack.write() on coroutine dispatcher threads.
 * - Uses [AudioAttributes.USAGE_MEDIA] with [AudioAttributes.CONTENT_TYPE_SPEECH] to stream
 *   PTT voice directly through the host device's loudspeaker without altering system audio mode
 *   to MODE_IN_COMMUNICATION, avoiding OS audio focus conflicts and background policy termination.
 * - Automatic inactivity watchdog gracefully finalizes playback if network drops before PTT_STOP.
 */
@Singleton
class PttAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:PttPlayer"
        private const val SAMPLE_RATE = 16000 // 16 kHz Wideband Voice Intercom
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val QUEUE_CAPACITY = 64 // ~6.4 seconds of 100ms chunks max buffer
        private const val INACTIVITY_TIMEOUT_MS = 2500L // 2.5s without audio triggers auto-stop
    }

    // Dedicated single-thread dispatcher guarantees AudioTrack creation, write, stop,
    // and release happen strictly sequentially on one native thread.
    private val audioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Sentinel-PttPlayerThread").apply {
            priority = Thread.MAX_PRIORITY
        }
    }
    private val audioDispatcher = audioExecutor.asCoroutineDispatcher()
    private val playerScope = CoroutineScope(audioDispatcher)

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var watchdogJob: Job? = null
    private var audioChannel = Channel<ByteArray>(capacity = QUEUE_CAPACITY)

    private val isSessionActive = AtomicBoolean(false)
    val isPlaying: Boolean get() = isSessionActive.get()

    private var previousVolume: Int? = null

    /** Callback invoked whenever PTT session terminates (via stop command or watchdog timeout). */
    var onSessionEnded: (() -> Unit)? = null

    @Synchronized
    fun startPttSession(micAudioRecordSessionId: Int? = null) {
        if (isSessionActive.get()) {
            resetWatchdog()
            return
        }

        isSessionActive.set(true)
        try {
            audioChannel.close()
        } catch (e: Exception) {
            // Ignore channel close exception
        }
        audioChannel = Channel(capacity = QUEUE_CAPACITY)

        playbackJob = playerScope.launch {
            try {
                runPlaybackLoop()
            } catch (e: Exception) {
                Log.e(TAG, "PTT playback loop encountered error: ${e.message}", e)
            } finally {
                cleanupAudioTrack()
                restoreVolume()
                isSessionActive.set(false)
                onSessionEnded?.invoke()
                Log.i(TAG, "PTT loudspeaker playback session finalized")
            }
        }

        resetWatchdog()
        Log.i(TAG, "PTT loudspeaker playback session initiated")
    }

    private suspend fun runPlaybackLoop() {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build AudioTrack: ${e.message}", e)
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack initialization failed (state=${track.state})")
            try { track.release() } catch (e: Exception) { /* no-op */ }
            return
        }

        audioTrack = track
        boostVolumeForPtt()

        try {
            track.setVolume(1.0f)
            track.play()
            Log.i(TAG, "AudioTrack playing at $SAMPLE_RATE Hz (buffer=$bufferSize)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack playback: ${e.message}", e)
            return
        }

        // Sequential consumption loop confined strictly to Sentinel-PttPlayerThread
        while (isSessionActive.get() && playerScope.isActive) {
            val chunk = audioChannel.receiveCatching().getOrNull() ?: break
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                try {
                    track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                } catch (e: Exception) {
                    Log.w(TAG, "AudioTrack write exception: ${e.message}")
                    break
                }
            }
        }
    }

    private fun boostVolumeForPtt() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val stream = AudioManager.STREAM_MUSIC
            val maxVol = audioManager.getStreamMaxVolume(stream)
            val curVol = audioManager.getStreamVolume(stream)
            val minAudible = (maxVol * 0.80f).toInt().coerceAtLeast(1)
            if (curVol < minAudible) {
                previousVolume = curVol
                audioManager.setStreamVolume(stream, minAudible, 0)
                Log.i(TAG, "Boosted media volume from $curVol to $minAudible / $maxVol for PTT clarity")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Volume boost warning: ${e.message}")
        }
    }

    private fun restoreVolume() {
        val prev = previousVolume ?: return
        previousVolume = null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, prev, 0)
            Log.i(TAG, "Restored media volume to previous level: $prev")
        } catch (e: Exception) {
            Log.w(TAG, "Volume restore warning: ${e.message}")
        }
    }

    private fun cleanupAudioTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack stop warning: ${e.message}")
        }
        try {
            track.release()
            Log.i(TAG, "AudioTrack released safely on dedicated thread")
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack release warning: ${e.message}")
        }
    }

    suspend fun playPcmChunk(pcmData: ByteArray) {
        if (!isSessionActive.get() || playbackJob?.isActive != true) {
            startPttSession()
        }
        resetWatchdog()
        val result = audioChannel.trySend(pcmData)
        if (!result.isSuccess) {
            Log.w(TAG, "PttPlayer buffer saturated or closed; dropped ${pcmData.size} bytes")
        }
    }

    private fun resetWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = CoroutineScope(Dispatchers.Default).launch {
            delay(INACTIVITY_TIMEOUT_MS)
            if (isSessionActive.get()) {
                Log.i(TAG, "PTT inactivity watchdog timed out ($INACTIVITY_TIMEOUT_MS ms) — closing session")
                stopPttSession()
            }
        }
    }

    @Synchronized
    fun stopPttSession() {
        if (!isSessionActive.get()) return
        Log.i(TAG, "Stopping PTT loudspeaker session")
        watchdogJob?.cancel()
        watchdogJob = null
        isSessionActive.set(false)
        try {
            audioChannel.close()
        } catch (e: Exception) {
            // Ignore
        }
        playbackJob?.cancel()
        playbackJob = null
    }
}
