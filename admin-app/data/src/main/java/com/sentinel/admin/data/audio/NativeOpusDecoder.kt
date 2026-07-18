package com.sentinel.admin.data.audio

import android.util.Log
import com.sentinel.shared.protocol.AudioConstants

/**
 * Opus decoder backed by native libopus via JNI.
 *
 * Configuration (per PROTOCOL.md / AudioConstants):
 * - Sample rate: 48000 Hz
 * - Channels: 1 (mono)
 *
 * Production invariants:
 * - A decode failure NEVER destroys the decoder. Only [close] does that.
 * - Decode failures return -1. The caller drops the frame and continues.
 * - [close] is idempotent — safe to call multiple times.
 * - Using the decoder after close returns -1 (no crash).
 * - JNI handle released exactly once via atomic swap to 0.
 *
 * Thread safety:
 * - Must be used from a single thread at a time.
 * - The caller ensures this via the audio coroutine dispatcher.
 */
class NativeOpusDecoder {

    companion object {
        private const val TAG = "Sentinel:OpusDec"
    }

    @Volatile
    private var handle: Long = 0L

    /**
     * Initializes the native Opus decoder.
     * Must be called before [decode].
     * Idempotent — closes existing decoder first if already initialized.
     *
     * @return true if initialization succeeded.
     */
    fun initialize(): Boolean {
        if (handle != 0L) {
            Log.w(TAG, "Already initialized — closing first")
            close()
        }

        if (!OpusDecoderJni.isLoaded) {
            Log.e(TAG, "Native opus library not loaded")
            return false
        }

        handle = try {
            OpusDecoderJni.nativeCreate(
                sampleRate = AudioConstants.SAMPLE_RATE,
                channels = AudioConstants.CHANNELS
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI call failed: ${e.message}")
            0L
        }

        if (handle == 0L) {
            Log.e(TAG, "Failed to create native Opus decoder")
            return false
        }

        Log.i(TAG, "Opus decoder initialized (rate=${AudioConstants.SAMPLE_RATE}, " +
            "ch=${AudioConstants.CHANNELS})")
        return true
    }

    /**
     * Decodes an Opus frame into PCM samples.
     *
     * @param opusData Encoded Opus data.
     * @param opusLen  Length of encoded data in bytes.
     * @param pcmOut   Output buffer for decoded 16-bit PCM. Must be at least [frameSize] elements.
     * @param frameSize Number of samples per channel (960 for 20ms at 48kHz).
     * @return Number of samples decoded, or -1 on error. Never throws.
     */
    fun decode(opusData: ByteArray, opusLen: Int, pcmOut: ShortArray, frameSize: Int): Int {
        val h = handle
        if (h == 0L) {
            Log.e(TAG, "Decode called on closed/uninitialized decoder")
            return -1
        }

        return OpusDecoderJni.nativeDecode(h, opusData, opusLen, pcmOut, frameSize)
    }

    /**
     * Performs Packet Loss Concealment for a missing frame.
     * Opus interpolates from previous decoder state.
     *
     * @param pcmOut   Output buffer for concealed PCM.
     * @param frameSize Number of samples per channel to generate.
     * @return Number of samples generated, or -1 on error.
     */
    fun decodePLC(pcmOut: ShortArray, frameSize: Int): Int {
        val h = handle
        if (h == 0L) {
            Log.e(TAG, "DecodePLC called on closed/uninitialized decoder")
            return -1
        }

        return OpusDecoderJni.nativeDecodePLC(h, pcmOut, frameSize)
    }

    /**
     * Returns true if the decoder is initialized and ready.
     */
    fun isInitialized(): Boolean = handle != 0L

    /**
     * Destroys the native Opus decoder and frees memory.
     * Idempotent — safe to call multiple times.
     * After close, decode() returns -1 (no crash).
     */
    fun close() {
        val h = handle
        if (h != 0L) {
            handle = 0L
            OpusDecoderJni.nativeDestroy(h)
            Log.i(TAG, "Opus decoder closed")
        }
    }
}
