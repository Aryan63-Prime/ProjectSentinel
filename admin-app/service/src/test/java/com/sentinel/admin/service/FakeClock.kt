package com.sentinel.admin.service

import com.sentinel.admin.domain.time.Clock
import java.util.concurrent.atomic.AtomicLong

/**
 * Deterministic clock for testing.
 *
 * Usage:
 * ```
 * val clock = FakeClock(startTime = 1000)
 * clock.advanceBy(5000) // now at 6000
 * assertEquals(6000, clock.currentTimeMillis())
 * ```
 */
class FakeClock(startTime: Long = 0L) : Clock {
    private val time = AtomicLong(startTime)

    override fun currentTimeMillis(): Long = time.get()

    fun advanceBy(millis: Long) {
        time.addAndGet(millis)
    }

    fun set(millis: Long) {
        time.set(millis)
    }
}
