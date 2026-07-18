package com.sentinel.host.data.audio

import android.util.Log
import com.sentinel.host.domain.audio.OpusEncoder
import com.sentinel.shared.protocol.AudioConstants

/**
 * Opus encoder backed by native libopus via JNI.
 *
 * Configuration (per PROTOCOL.md / AudioConstants):
 * - Sample rate: 48000 Hz
 * - Channels: 1 (mono)
 * - Application: VOIP (2048) — optimized for voice
 * - Bitrate: 24000 bps (adaptive via VBR)
 *
 * Thread safety:
 * - Must be used from a single thread at a time.
 * - The caller ensures this via the audio coroutine dispatcher.
 *
 * Resource lifecycle:
 * - [close] destroys the native encoder and zeroes the handle.
 * - Double-close is safe (idempotent).
 * - Using the encoder after close returns -1 (no crash).
 */
class NativeOpusEncoder : OpusEncoder {

    companion object {
        private const val TAG = "Sentinel:OpusEnc"

        /** OPUS_APPLICATION_VOIP — optimized for voice signals. */
        private const val APPLICATION_VOIP = 2048
    }

    @Volatile
    private var handle: Long = 0L

    /**
     * Initializes the native Opus encoder.
     * Must be called before [encode].
     *
     * @return true if initialization succeeded.
     */
    fun initialize(): Boolean {
        if (handle != 0L) {
            Log.w(TAG, "Already initialized — closing first")
            close()
        }

        handle = OpusJni.nativeCreate(
            sampleRate = AudioConstants.SAMPLE_RATE,
            channels = AudioConstants.CHANNELS,
            application = APPLICATION_VOIP,
            bitrate = AudioConstants.INITIAL_BITRATE
        )

        if (handle == 0L) {
            Log.e(TAG, "Failed to create native Opus encoder")
            return false
        }

        Log.i(TAG, "Opus encoder initialized (rate=${AudioConstants.SAMPLE_RATE}, " +
            "ch=${AudioConstants.CHANNELS}, bitrate=${AudioConstants.INITIAL_BITRATE})")
        return true
    }

    override fun encode(pcm: ShortArray, frameSize: Int, output: ByteArray, maxOutput: Int): Int {
        val h = handle
        if (h == 0L) {
            Log.e(TAG, "Encode called on closed/uninitialized encoder")
            return -1
        }

        return OpusJni.nativeEncode(h, pcm, frameSize, output, maxOutput)
    }

    override fun close() {
        val h = handle
        if (h != 0L) {
            handle = 0L
            OpusJni.nativeDestroy(h)
            Log.i(TAG, "Opus encoder closed")
        }
    }
}
