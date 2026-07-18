package com.sentinel.admin.data.audio

import java.util.TreeMap

/**
 * Sorted jitter buffer for audio frames.
 *
 * Uses TreeMap<Long, ByteArray> ordered by sequence number.
 * Handles out-of-order, duplicate, and late packets.
 * Supports wrap-around of uint32 sequence numbers.
 *
 * Production invariants:
 * - Never blocks.
 * - No allocations inside pop() except unavoidable TreeMap removal.
 * - Thread safety: must be externally synchronized (AudioMonitor's playback coroutine).
 *
 * Adaptive warm-up:
 * - Starts playback after [minDepth] frames buffered.
 * - Grows target to [maxDepth] if jitter detected (out-of-order arrivals).
 * - Shrinks back to [minDepth] after [stableThreshold] consecutive in-order deliveries.
 *
 * @param minDepth Minimum frames before playback starts (default 2 = 40ms).
 * @param maxDepth Maximum buffer target during jitter (default 5 = 100ms).
 * @param maxLateDistance Frames behind playhead considered "too late" and dropped.
 * @param stableThreshold Consecutive in-order deliveries before shrinking target.
 */
class JitterBuffer(
    private val minDepth: Int = 2,
    private val maxDepth: Int = 5,
    private val maxLateDistance: Int = 10,
    private val stableThreshold: Int = 50
) {

    private val buffer = TreeMap<Long, ByteArray>()

    /** Next expected sequence for playback. */
    private var playbackHead: Long = -1L

    /** Current adaptive target depth. */
    private var targetDepth: Int = minDepth

    /** Consecutive in-order deliveries (for adaptive shrinking). */
    private var consecutiveInOrder: Int = 0

    // ---- Statistics ----
    var received: Long = 0L; private set
    var delivered: Long = 0L; private set
    var droppedLate: Long = 0L; private set
    var droppedDuplicate: Long = 0L; private set

    /**
     * Result of pushing a frame.
     */
    enum class PushResult {
        ACCEPTED, DUPLICATE, LATE
    }

    /**
     * Result of popping a frame.
     */
    sealed interface PopResult {
        /** Frame available for decoding. */
        data class Frame(val sequence: Long, val payload: ByteArray) : PopResult
        /** Expected sequence is missing — caller should perform PLC. */
        data class Missing(val expectedSequence: Long) : PopResult
        /** Buffer is empty. */
        data object Empty : PopResult
    }

    /**
     * Pushes a frame into the buffer.
     *
     * @return [PushResult] indicating whether the frame was accepted, duplicate, or late.
     */
    fun push(sequence: Long, payload: ByteArray): PushResult {
        received++

        // Check for duplicate (always, regardless of playback state)
        if (buffer.containsKey(sequence)) {
            droppedDuplicate++
            return PushResult.DUPLICATE
        }

        // Late-packet check only applies after playback has started
        if (playbackHead >= 0) {
            val distance = sequenceDistance(sequence, playbackHead)
            if (distance < -maxLateDistance) {
                droppedLate++
                return PushResult.LATE
            }
        }

        // Detect jitter (out-of-order arrival)
        if (buffer.isNotEmpty() && sequence < buffer.lastKey()) {
            consecutiveInOrder = 0
            if (targetDepth < maxDepth) {
                targetDepth = (targetDepth + 1).coerceAtMost(maxDepth)
            }
        }

        buffer[sequence] = payload
        return PushResult.ACCEPTED
    }

    /**
     * Pops the next frame in sequence order.
     *
     * If the next expected sequence is missing but later frames exist,
     * returns [PopResult.Missing] so the caller can perform PLC.
     * Then advances the playback head past the gap.
     *
     * @return The next frame, a missing signal, or empty.
     */
    fun pop(): PopResult {
        if (buffer.isEmpty()) return PopResult.Empty

        // Initialize playback head on first pop — uses smallest buffered sequence
        if (playbackHead < 0) {
            playbackHead = buffer.firstKey()
        }

        val firstKey = buffer.firstKey()

        // If the playback head matches the first buffered frame, deliver it
        if (firstKey == playbackHead) {
            val payload = buffer.remove(firstKey)!!
            delivered++
            playbackHead = nextSequence(playbackHead)

            // Track stability for adaptive shrinking
            consecutiveInOrder++
            if (consecutiveInOrder >= stableThreshold && targetDepth > minDepth) {
                targetDepth = (targetDepth - 1).coerceAtLeast(minDepth)
                consecutiveInOrder = 0
            }

            return PopResult.Frame(firstKey, payload)
        }

        // If the first buffered frame is ahead of playback head, there's a gap
        val gap = sequenceDistance(firstKey, playbackHead)
        if (gap > 0) {
            val missing = playbackHead
            playbackHead = nextSequence(playbackHead)
            return PopResult.Missing(missing)
        }

        // First frame is behind playback head (shouldn't happen, but handle gracefully)
        buffer.remove(firstKey)
        droppedLate++
        return PopResult.Empty
    }

    /**
     * Whether the buffer has enough frames for playback to start.
     */
    fun isReady(): Boolean = buffer.size >= targetDepth

    /**
     * Current number of frames in the buffer.
     */
    fun size(): Int = buffer.size

    /**
     * Current adaptive target depth.
     */
    fun currentTargetDepth(): Int = targetDepth

    /**
     * Clears the buffer and resets state.
     * Does NOT reset statistics.
     */
    fun clear() {
        buffer.clear()
        playbackHead = -1L
        targetDepth = minDepth
        consecutiveInOrder = 0
    }

    /**
     * Resets everything including statistics.
     */
    fun reset() {
        clear()
        received = 0
        delivered = 0
        droppedLate = 0
        droppedDuplicate = 0
    }

    // ============================================================
    // Sequence arithmetic (wrap-around aware)
    // ============================================================

    companion object {
        /**
         * Signed distance between two uint32 sequence numbers.
         * Handles wrap-around: if a is slightly less than b after wrap, returns negative.
         *
         * Uses signed 32-bit comparison: (a - b) cast to Int.
         * Range: -2^31 to 2^31-1.
         */
        fun sequenceDistance(a: Long, b: Long): Int {
            return ((a - b) and 0xFFFFFFFFL).toInt()
        }

        /**
         * Next sequence number with uint32 wrap-around.
         */
        fun nextSequence(seq: Long): Long {
            return (seq + 1) and 0xFFFFFFFFL
        }
    }
}
