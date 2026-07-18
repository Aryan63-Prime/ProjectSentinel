package com.sentinel.host.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.sentinel.host.domain.audio.AudioRecorder
import com.sentinel.shared.protocol.AudioConstants

/**
 * Wraps [android.media.AudioRecord] behind the [AudioRecorder] interface.
 *
 * Configuration (per PROTOCOL.md / AudioConstants):
 * - Source: VOICE_COMMUNICATION (echo cancellation + noise suppression)
 * - Sample rate: 48000 Hz
 * - Channel: Mono
 * - Encoding: PCM 16-bit
 * - Buffer: 2x minimum (double-buffered for smooth reads)
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

        // Double-buffer for smooth reads
        val bufferSize = minBufferSize * 2

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AudioConstants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize (state=${audioRecord?.state})")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.i(TAG, "Recording started (rate=${AudioConstants.SAMPLE_RATE}, " +
                "bufferSize=$bufferSize, minBuffer=$minBufferSize)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            audioRecord?.release()
            audioRecord = null
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
