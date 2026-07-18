package com.sentinel.admin.data.remote

import com.sentinel.admin.domain.model.ReconnectConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for [ReconnectPolicy].
 *
 * Covers:
 * - Exponential backoff calculation
 * - Delay capping at maxDelay
 * - Jitter application
 * - shouldRetry with finite attempts
 * - shouldRetry with unlimited attempts
 * - Zero jitter (deterministic)
 * - Delay never goes negative
 */
class ReconnectPolicyTest {

    // ============================================================
    // Deterministic tests (jitter = 0)
    // ============================================================

    @Test
    fun `getDelayMs returns initialDelay for attempt 0 with no jitter`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 30_000,
                multiplier = 2.0,
                jitterFactor = 0.0
            )
        )

        assertEquals(1000L, policy.getDelayMs(0))
    }

    @Test
    fun `getDelayMs doubles each attempt with no jitter`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 60_000,
                multiplier = 2.0,
                jitterFactor = 0.0
            )
        )

        assertEquals(1000L, policy.getDelayMs(0))
        assertEquals(2000L, policy.getDelayMs(1))
        assertEquals(4000L, policy.getDelayMs(2))
        assertEquals(8000L, policy.getDelayMs(3))
        assertEquals(16000L, policy.getDelayMs(4))
        assertEquals(32000L, policy.getDelayMs(5))
    }

    @Test
    fun `getDelayMs caps at maxDelay`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 5000,
                multiplier = 2.0,
                jitterFactor = 0.0
            )
        )

        assertEquals(4000L, policy.getDelayMs(2))
        assertEquals(5000L, policy.getDelayMs(3)) // Capped
        assertEquals(5000L, policy.getDelayMs(4)) // Still capped
        assertEquals(5000L, policy.getDelayMs(10)) // Way past cap
    }

    // ============================================================
    // Jitter tests
    // ============================================================

    @Test
    fun `getDelayMs with jitter stays within expected range`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 30_000,
                multiplier = 2.0,
                jitterFactor = 0.2
            )
        )

        // Run many times to check statistical bounds
        repeat(100) {
            val delay = policy.getDelayMs(0)
            // 1000 ± 20% → [800, 1200]
            assertTrue("Delay $delay should be >= 800", delay >= 800)
            assertTrue("Delay $delay should be <= 1200", delay <= 1200)
        }
    }

    @Test
    fun `getDelayMs with deterministic random`() {
        val fixedRandom = Random(42)
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 30_000,
                multiplier = 2.0,
                jitterFactor = 0.2
            ),
            random = fixedRandom
        )

        // Should be deterministic
        val delay = policy.getDelayMs(0)
        assertTrue("Delay should be positive", delay > 0)
    }

    @Test
    fun `getDelayMs never returns negative`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 100,
                maxDelayMs = 30_000,
                multiplier = 2.0,
                jitterFactor = 1.0 // Maximum jitter
            )
        )

        repeat(1000) { attempt ->
            val delay = policy.getDelayMs(attempt % 15)
            assertTrue("Delay should never be negative: $delay", delay >= 0)
        }
    }

    // ============================================================
    // shouldRetry tests
    // ============================================================

    @Test
    fun `shouldRetry returns true for attempts within limit`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(maxAttempts = 5)
        )

        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(4))
    }

    @Test
    fun `shouldRetry returns false at and beyond limit`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(maxAttempts = 5)
        )

        assertFalse(policy.shouldRetry(5))
        assertFalse(policy.shouldRetry(6))
        assertFalse(policy.shouldRetry(100))
    }

    @Test
    fun `shouldRetry with unlimited always returns true`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(maxAttempts = 0)
        )

        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(100))
        assertTrue(policy.shouldRetry(999_999))
    }

    @Test
    fun `maxAttempts returns configured value`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(maxAttempts = 7)
        )

        assertEquals(7, policy.maxAttempts)
    }

    // ============================================================
    // Default config
    // ============================================================

    @Test
    fun `default config values are reasonable`() {
        val config = ReconnectConfig()

        assertEquals(1_000L, config.initialDelayMs)
        assertEquals(30_000L, config.maxDelayMs)
        assertEquals(10, config.maxAttempts)
        assertEquals(2.0, config.multiplier, 0.001)
        assertEquals(0.2, config.jitterFactor, 0.001)
    }
}
