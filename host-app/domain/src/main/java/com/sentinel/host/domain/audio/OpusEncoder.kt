package com.sentinel.host.domain.audio

import java.io.Closeable

/**
 * Abstraction over Opus encoding.
 *
 * Implementations wrap native libopus or test fakes.
 * Each instance must be used from a single thread at a time.
 *
 * Lifecycle:
 * - Create via DI or factory.
 * - Call [encode] for each PCM frame.
 * - Call [close] when done to release native resources.
 * - Supports Kotlin `.use {}` for RAII-style cleanup.
 *
 * Buffer ownership:
 * - Caller owns [pcm] and [output] arrays.
 * - Caller is responsible for reusing buffers to avoid allocations.
 */
interface OpusEncoder : Closeable {

    /**
     * Encodes a frame of PCM audio into Opus.
     *
     * @param pcm       PCM samples (16-bit signed, mono).
     * @param frameSize Number of samples per channel (960 for 20ms at 48kHz).
     * @param output    Output buffer for encoded Opus data.
     * @param maxOutput Maximum number of bytes to write to [output].
     * @return          Number of bytes written to [output], or negative on error.
     */
    fun encode(pcm: ShortArray, frameSize: Int, output: ByteArray, maxOutput: Int): Int

    /**
     * Releases native encoder resources.
     * Safe to call multiple times (idempotent).
     */
    override fun close()
}
