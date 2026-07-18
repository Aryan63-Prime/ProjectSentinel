package com.sentinel.host.data.audio

/**
 * JNI declarations for the native Opus encoder bridge.
 *
 * Loads the `opus_jni` shared library which statically links libopus.
 * All native methods operate on a handle (pointer) returned by [nativeCreate].
 *
 * Thread safety: Each encoder handle must be used from a single thread.
 * The [NativeOpusEncoder] wrapper enforces this via the audio dispatcher.
 */
internal object OpusJni {

    init {
        System.loadLibrary("opus_jni")
    }

    /**
     * Creates a new Opus encoder.
     * @return Native handle, or 0 on failure.
     */
    @JvmStatic
    external fun nativeCreate(
        sampleRate: Int,
        channels: Int,
        application: Int,
        bitrate: Int
    ): Long

    /**
     * Encodes PCM samples into Opus.
     * @return Number of bytes written, or negative on error.
     */
    @JvmStatic
    external fun nativeEncode(
        handle: Long,
        pcm: ShortArray,
        frameSize: Int,
        output: ByteArray,
        maxOutput: Int
    ): Int

    /**
     * Destroys the encoder and frees native memory.
     * Safe to call with handle=0.
     */
    @JvmStatic
    external fun nativeDestroy(handle: Long)
}
