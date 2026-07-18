package com.sentinel.admin.domain.time

/**
 * Abstraction over system clock.
 *
 * Allows tests to use [FakeClock] with deterministic time
 * instead of sleeping. Used by HeartbeatScheduler and
 * ReconnectPolicy for timestamp tracking.
 */
interface Clock {
    /** Returns current time in milliseconds (epoch). */
    fun currentTimeMillis(): Long
}

/**
 * Production clock backed by [System.currentTimeMillis].
 */
class SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
