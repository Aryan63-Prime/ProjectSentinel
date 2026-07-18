package com.sentinel.admin.data.remote

import java.util.concurrent.atomic.AtomicLong

/**
 * Generates monotonically increasing sequence numbers for protocol messages.
 * Uses AtomicLong — thread-safe, no wraparound concern.
 */
class SequenceGenerator {
    private val counter = AtomicLong(0)

    fun next(): Long = counter.incrementAndGet()

    fun reset() {
        counter.set(0)
    }
}
