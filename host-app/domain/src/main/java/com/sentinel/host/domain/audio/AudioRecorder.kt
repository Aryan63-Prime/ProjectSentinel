package com.sentinel.host.domain.audio

import java.io.Closeable

/**
 * Abstraction over platform audio recording.
 *
 * Implementations wrap Android's AudioRecord (or test fakes).
 * Each instance must be used from a single thread at a time.
 *
 * State transitions:
 * - Created → [start] → Recording → [stop] → Stopped → [release]
 * - [release] frees all native resources.
 * - Supports Kotlin `.use {}` for RAII-style cleanup.
 *
 * Buffer ownership:
 * - Caller owns the buffer passed to [read].
 * - Caller is responsible for reusing buffers to avoid allocations.
 */
interface AudioRecorder : Closeable {

    /** Whether the recorder is currently capturing audio. */
    val isRecording: Boolean

    /**
     * Starts audio capture.
     * Must be called before [read].
     *
     * @return true if recording started successfully.
     */
    fun start(): Boolean

    /**
     * Stops audio capture.
     * The recorder can be restarted with [start].
     */
    fun stop()

    /**
     * Reads PCM samples into the buffer.
     * Blocks until [size] samples are available or an error occurs.
     *
     * @param buffer Destination for PCM samples (16-bit signed).
     * @param offset Starting index in [buffer].
     * @param size   Number of samples to read.
     * @return       Number of samples actually read, or negative on error.
     */
    fun read(buffer: ShortArray, offset: Int, size: Int): Int

    /**
     * Releases all native audio resources.
     * The recorder cannot be used after this call.
     * Safe to call multiple times (idempotent).
     */
    override fun close()
}
