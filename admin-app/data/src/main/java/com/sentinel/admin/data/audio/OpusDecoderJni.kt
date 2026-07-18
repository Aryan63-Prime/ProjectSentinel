package com.sentinel.admin.data.audio

/**
 * JNI declarations for the native Opus decoder bridge.
 *
 * Loads the `opus_decoder_jni` shared library which statically links libopus.
 * All native methods operate on a handle (pointer) returned by [nativeCreate].
 *
 * Thread safety: Each decoder handle must be used from a single thread.
 * The [NativeOpusDecoder] wrapper enforces this via the audio dispatcher.
 */
internal object OpusDecoderJni {

    @Volatile
    var isLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("opus_decoder_jni")
            isLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            // Expected in JVM unit tests where native lib is unavailable.
            // All native methods will throw UnsatisfiedLinkError if called.
            isLoaded = false
        }
    }

    /**
     * Creates a new Opus decoder.
     * @return Native handle, or 0 on failure.
     */
    @JvmStatic
    external fun nativeCreate(sampleRate: Int, channels: Int): Long

    /**
     * Decodes Opus data into PCM samples.
     * @return Number of samples decoded per channel, or negative on error.
     */
    @JvmStatic
    external fun nativeDecode(
        handle: Long,
        opusData: ByteArray,
        opusLen: Int,
        pcmOut: ShortArray,
        frameSize: Int
    ): Int

    /**
     * Performs Packet Loss Concealment by generating interpolated audio.
     * @return Number of samples generated, or negative on error.
     */
    @JvmStatic
    external fun nativeDecodePLC(
        handle: Long,
        pcmOut: ShortArray,
        frameSize: Int
    ): Int

    /**
     * Destroys the decoder and frees native memory.
     * Safe to call with handle=0. Must be called exactly once per handle.
     */
    @JvmStatic
    external fun nativeDestroy(handle: Long)
}
