package com.sentinel.host.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioSessionTest {

    private lateinit var session: AudioSession

    @Before
    fun setUp() {
        session = AudioSession()
    }

    // ================================================================
    // Initial state
    // ================================================================

    @Test
    fun `initial state is inactive with zero counters`() {
        assertFalse(session.isActive)
        assertEquals(0L, session.framesEncoded)
        assertEquals(0L, session.droppedFrames)
        assertEquals(0L, session.totalEncodeTimeNs)
        assertEquals(0L, session.maxEncodeTimeNs)
        assertEquals(0L, session.startTimeMs)
    }

    // ================================================================
    // Start / stop lifecycle
    // ================================================================

    @Test
    fun `start sets active and timestamp`() {
        session.start()
        assertTrue(session.isActive)
        assertTrue(session.startTimeMs > 0)
    }

    @Test
    fun `stop sets inactive`() {
        session.start()
        session.stop()
        assertFalse(session.isActive)
    }

    @Test
    fun `start resets counters from previous session`() {
        session.start()
        session.recordEncode(1000)
        session.recordDrop()
        assertEquals(1L, session.framesEncoded)
        assertEquals(1L, session.droppedFrames)

        session.start()
        assertEquals(0L, session.framesEncoded)
        assertEquals(0L, session.droppedFrames)
    }

    // ================================================================
    // Frame counting
    // ================================================================

    @Test
    fun `recordEncode increments frame count`() {
        session.start()
        session.recordEncode(100)
        session.recordEncode(200)
        session.recordEncode(300)
        assertEquals(3L, session.framesEncoded)
    }

    @Test
    fun `recordDrop increments drop count`() {
        session.start()
        session.recordDrop()
        session.recordDrop()
        assertEquals(2L, session.droppedFrames)
    }

    // ================================================================
    // Encode time statistics
    // ================================================================

    @Test
    fun `recordEncode tracks total encode time`() {
        session.start()
        session.recordEncode(1000)
        session.recordEncode(2000)
        assertEquals(3000L, session.totalEncodeTimeNs)
    }

    @Test
    fun `recordEncode tracks max encode time`() {
        session.start()
        session.recordEncode(1000)
        session.recordEncode(5000)
        session.recordEncode(3000)
        assertEquals(5000L, session.maxEncodeTimeNs)
    }

    @Test
    fun `averageEncodeTimeUs calculates correctly`() {
        session.start()
        session.recordEncode(1000_000) // 1ms = 1000µs
        session.recordEncode(3000_000) // 3ms = 3000µs
        // Average = 2000µs
        assertEquals(2000L, session.averageEncodeTimeUs)
    }

    @Test
    fun `averageEncodeTimeUs returns 0 when no frames`() {
        assertEquals(0L, session.averageEncodeTimeUs)
    }

    // ================================================================
    // Reset
    // ================================================================

    @Test
    fun `reset clears all counters`() {
        session.start()
        session.recordEncode(5000)
        session.recordDrop()

        session.reset()

        assertFalse(session.isActive)
        assertEquals(0L, session.framesEncoded)
        assertEquals(0L, session.droppedFrames)
        assertEquals(0L, session.totalEncodeTimeNs)
        assertEquals(0L, session.maxEncodeTimeNs)
        assertEquals(0L, session.startTimeMs)
    }

    // ================================================================
    // p99 latency
    // ================================================================

    @Test
    fun `encodeTimeP99Us returns 0 with fewer than 10 samples`() {
        session.start()
        for (i in 1..9) {
            session.recordEncode(1000_000L) // 1ms
        }
        assertEquals(0L, session.encodeTimeP99Us)
    }

    @Test
    fun `encodeTimeP99Us returns correct p99 with sufficient samples`() {
        session.start()
        // Record 90 fast encodes at 100µs and 10 slow at 5ms
        for (i in 1..90) {
            session.recordEncode(100_000L) // 100µs
        }
        for (i in 1..10) {
            session.recordEncode(5_000_000L) // 5000µs
        }

        // p99 should be >= the slow encode time
        // With 100 samples, p99 index = floor(99 * 0.99) = 98
        // Sorted: [100µs × 90, 5000µs × 10] → index 98 = 5000µs
        val p99 = session.encodeTimeP99Us
        assertTrue("p99 should be >= 5000µs, got ${p99}µs", p99 >= 5000)
    }

    @Test
    fun `encodeTimeP99Us resets with session`() {
        session.start()
        for (i in 1..100) {
            session.recordEncode(5_000_000L) // 5ms
        }
        assertTrue(session.encodeTimeP99Us > 0)

        session.start() // Reset
        assertEquals(0L, session.encodeTimeP99Us)
    }

    // ================================================================
    // Thread safety (basic verification)
    // ================================================================

    @Test
    fun `concurrent recordEncode is safe`() {
        session.start()
        val threads = (1..10).map {
            Thread {
                for (i in 1..100) {
                    session.recordEncode(1000)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1000L, session.framesEncoded)
    }

    @Test
    fun `concurrent recordDrop is safe`() {
        session.start()
        val threads = (1..10).map {
            Thread {
                for (i in 1..100) {
                    session.recordDrop()
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1000L, session.droppedFrames)
    }
}
