package com.sentinel.host.data.remote

import java.util.concurrent.atomic.AtomicLong

/**
 * Generates monotonically increasing sequence numbers for protocol messages.
 * Uses AtomicLong per user requirement (no wraparound concern).
 * Thread-safe.
 */
class SequenceGenerator {
    private val counter = AtomicLong(0)

    fun next(): Long = counter.incrementAndGet()

    fun reset() {
        counter.set(0)
    }
}
