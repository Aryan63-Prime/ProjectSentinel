package com.sentinel.admin.data.audio

/**
 * Abstraction over audio output for unit testability.
 *
 * Production implementation wraps [android.media.AudioTrack].
 * Tests provide a fake that records written samples.
 *
 * All methods are idempotent — safe to call multiple times in any order.
 */
interface AudioOutput {
    /** Initializes the audio output. Returns true on success. */
    fun initialize(): Boolean

    /**
     * Writes PCM samples to the output.
     * Must never block the caller beyond the time needed to enqueue.
     *
     * @param pcm PCM samples (16-bit signed).
     * @param offset Starting offset in the array.
     * @param size Number of samples to write.
     * @return Number of samples actually written, or negative on error.
     */
    fun write(pcm: ShortArray, offset: Int, size: Int): Int

    /** Pauses playback. Idempotent. */
    fun pause()

    /** Resumes playback after pause. Idempotent. */
    fun resume()

    /** Releases all resources. Idempotent. After release, write() returns -1. */
    fun release()
}
