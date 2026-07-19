package com.sentinel.admin.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.sentinel.shared.protocol.AudioConstants

/**
 * Production [AudioOutput] backed by [AudioTrack].
 *
 * Configuration:
 * - 48 kHz, Mono, PCM 16-bit, MODE_STREAM
 * - USAGE_VOICE_COMMUNICATION + CONTENT_TYPE_SPEECH
 * - Buffer size: max(minBufferSize * 2, 4 frames worth)
 *
 * Production invariants:
 * - All methods are idempotent.
 * - [release] can be called multiple times safely.
 * - [write] returns -1 after release (never crashes).
 * - Never blocks beyond AudioTrack's internal enqueue.
 */
class AudioTrackOutput : AudioOutput {

    companion object {
        private const val TAG = "Sentinel:AudioTrack"
    }

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var released = false

    override fun initialize(): Boolean {
        if (track != null) {
            Log.w(TAG, "Already initialized — releasing first")
            release()
        }

        released = false

        val minBuffer = AudioTrack.getMinBufferSize(
            AudioConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer <= 0) {
            Log.e(TAG, "getMinBufferSize returned $minBuffer")
            return false
        }

        // Use 2x min buffer or 4 frames, whichever is larger
        val fourFrames = AudioConstants.SAMPLES_PER_FRAME * 4 * 2 // 2 bytes per sample
        val bufferSize = maxOf(minBuffer * 2, fourFrames)

        return try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AudioConstants.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track?.play()
            Log.i(TAG, "AudioTrack initialized (buffer=$bufferSize, minBuffer=$minBuffer)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioTrack: ${e.message}")
            track = null
            false
        }
    }

    override fun write(pcm: ShortArray, offset: Int, size: Int): Int {
        val t = track
        if (t == null || released) return -1

        return try {
            t.write(pcm, offset, size)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack write failed: ${e.message}")
            -1
        }
    }

    override fun pause() {
        try {
            track?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack pause failed: ${e.message}")
        }
    }

    override fun resume() {
        try {
            track?.play()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack resume failed: ${e.message}")
        }
    }

    override fun release() {
        released = true
        val t = track
        if (t != null) {
            track = null
            try {
                t.stop()
            } catch (_: Exception) { /* may throw if not playing */ }
            try {
                t.release()
            } catch (_: Exception) { /* safety */ }
            Log.i(TAG, "AudioTrack released")
        }
    }
}
