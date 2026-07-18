package com.sentinel.host.data.audio

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks audio pipeline statistics for debugging and monitoring.
 *
 * Thread-safe via atomic counters — can be read from any thread
 * while the audio pipeline writes from the audio dispatcher.
 *
 * Statistics:
 * - [framesEncoded] — total PCM frames successfully encoded
 * - [droppedFrames] — frames that failed to encode or send
 * - [totalEncodeTimeNs] — cumulative encoding time in nanoseconds
 * - [maxEncodeTimeNs] — worst-case encoding time
 * - [encodeTimeP99Us] — p99 encoding time in microseconds (from last [P99_WINDOW_SIZE] samples)
 * - [startTimeMs] — when the session started
 *
 * Usage:
 * - Call [recordEncode] after each successful encode with duration.
 * - Call [recordDrop] when a frame is dropped.
 * - Call [reset] when starting a new session.
 */
class AudioSession {

    companion object {
        /**
         * Number of recent samples kept for p99 calculation.
         * At 50fps (20ms frames), 500 samples = 10 seconds of history.
         */
        const val P99_WINDOW_SIZE = 500
    }

    private val _framesEncoded = AtomicLong(0)
    private val _droppedFrames = AtomicLong(0)
    private val _totalEncodeTimeNs = AtomicLong(0)
    private val _maxEncodeTimeNs = AtomicLong(0)

    /**
     * Ring buffer of recent encode times for p99 calculation.
     * Synchronized on [latencyLock] for writes; snapshot for reads.
     */
    private val latencySamples = LongArray(P99_WINDOW_SIZE)
    private val latencyLock = Any()
    private var latencyIndex = 0
    private var latencyCount = 0

    @Volatile
    var startTimeMs: Long = 0L
        private set

    @Volatile
    var isActive: Boolean = false
        private set

    val framesEncoded: Long get() = _framesEncoded.get()
    val droppedFrames: Long get() = _droppedFrames.get()
    val totalEncodeTimeNs: Long get() = _totalEncodeTimeNs.get()
    val maxEncodeTimeNs: Long get() = _maxEncodeTimeNs.get()

    /** Average encode time in microseconds, or 0 if no frames encoded. */
    val averageEncodeTimeUs: Long
        get() {
            val frames = framesEncoded
            return if (frames > 0) totalEncodeTimeNs / frames / 1000 else 0
        }

    /**
     * p99 encode time in microseconds from the last [P99_WINDOW_SIZE] samples.
     * Returns 0 if fewer than 10 samples have been recorded.
     */
    val encodeTimeP99Us: Long
        get() {
            val snapshot: LongArray
            val count: Int
            synchronized(latencyLock) {
                count = latencyCount
                if (count < 10) return 0
                snapshot = latencySamples.copyOf(minOf(count, P99_WINDOW_SIZE))
            }
            val sampleCount = minOf(count, P99_WINDOW_SIZE)
            val sorted = snapshot.copyOf(sampleCount)
            sorted.sort()
            val p99Index = ((sampleCount - 1) * 0.99).toInt()
            return sorted[p99Index] / 1000 // ns → µs
        }

    /** Session duration in milliseconds, or 0 if not active. */
    val durationMs: Long
        get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0

    /**
     * Starts a new session. Resets all counters.
     */
    fun start() {
        reset()
        startTimeMs = System.currentTimeMillis()
        isActive = true
    }

    /**
     * Stops the current session.
     */
    fun stop() {
        isActive = false
    }

    /**
     * Records a successful encode with its duration.
     *
     * @param encodeTimeNs Time taken to encode in nanoseconds.
     */
    fun recordEncode(encodeTimeNs: Long) {
        _framesEncoded.incrementAndGet()
        _totalEncodeTimeNs.addAndGet(encodeTimeNs)

        // Update max encode time (lock-free CAS loop)
        var currentMax: Long
        do {
            currentMax = _maxEncodeTimeNs.get()
            if (encodeTimeNs <= currentMax) break
        } while (!_maxEncodeTimeNs.compareAndSet(currentMax, encodeTimeNs))

        // Record in ring buffer for p99
        synchronized(latencyLock) {
            latencySamples[latencyIndex] = encodeTimeNs
            latencyIndex = (latencyIndex + 1) % P99_WINDOW_SIZE
            latencyCount++
        }
    }

    /**
     * Records a dropped frame.
     */
    fun recordDrop() {
        _droppedFrames.incrementAndGet()
    }

    /**
     * Resets all counters and timestamps.
     */
    fun reset() {
        _framesEncoded.set(0)
        _droppedFrames.set(0)
        _totalEncodeTimeNs.set(0)
        _maxEncodeTimeNs.set(0)
        synchronized(latencyLock) {
            latencySamples.fill(0)
            latencyIndex = 0
            latencyCount = 0
        }
        startTimeMs = 0
        isActive = false
    }
}
