package com.sentinel.host.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import com.sentinel.host.domain.audio.AudioRecorder
import com.sentinel.shared.protocol.AudioConstants

/**
 * Wraps [android.media.AudioRecord] behind the [AudioRecorder] interface.
 *
 * Configuration (per PROTOCOL.md / AudioConstants):
 * - Source: VOICE_RECOGNITION (ideal voice clarity, noise/echo suppressed but not gated)
 * - Sample rate: 48000 Hz
 * - Channel: Mono
 * - Encoding: PCM 16-bit
 * - Buffer: Max of 4x minimum or 64KB (prevent buffer underruns/crackle)
 *
 * State transitions:
 * - Created → [start] → Recording → [stop] → Stopped → [close]
 * - [close] calls stop() then release() on the native AudioRecord.
 *
 * Thread safety:
 * - Must be used from a single thread (the audio coroutine dispatcher).
 *
 * Permission:
 * - Caller must verify RECORD_AUDIO permission before calling [start].
 * - Uses @SuppressLint("MissingPermission") — permission is caller's responsibility.
 */
class AndroidAudioRecorder : AudioRecorder {

    companion object {
        private const val TAG = "Sentinel:AudioRec"
    }

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    @Volatile
    override var isRecording: Boolean = false
        private set

    /** Minimum buffer size calculated from AudioRecord API. */
    private val minBufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(
            AudioConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    }

    @SuppressLint("MissingPermission")
    override fun start(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording — stopping first")
            stop()
        }

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $minBufferSize")
            return false
        }

        // Increase buffer to prevent overflow crackle
        val bufferSize = Math.max(minBufferSize * 4, 65536)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AudioConstants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord = record

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize (state=${record.state})")
                record.release()
                audioRecord = null
                return false
            }

            // Note: Hardware NoiseSuppressor, AcousticEchoCanceler, and AGC are automatically
            // initialized by the OS audio hardware layer when using VOICE_COMMUNICATION source.
            // Programmatic instantiation is skipped to avoid redundant filters causing cutouts.

            record.startRecording()
            isRecording = true
            Log.i(TAG, "Recording started (rate=${AudioConstants.SAMPLE_RATE}, " +
                "bufferSize=$bufferSize, minBuffer=$minBufferSize)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            audioRecord?.release()
            audioRecord = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            automaticGainControl?.release()
            automaticGainControl = null
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
            return false
        }
    }

    override fun stop() {
        if (!isRecording) return

        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord.stop() failed: ${e.message}")
        }
        noiseSuppressor?.release()
        noiseSuppressor = null
        automaticGainControl?.release()
        automaticGainControl = null
        acousticEchoCanceler?.release()
        acousticEchoCanceler = null
        isRecording = false
        Log.i(TAG, "Recording stopped")
    }

    override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
        val record = audioRecord ?: return -1
        return record.read(buffer, offset, size)
    }

    override fun close() {
        stop()
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord.release() failed: ${e.message}")
        }
        audioRecord = null
        Log.i(TAG, "AudioRecord released")
    }
}
