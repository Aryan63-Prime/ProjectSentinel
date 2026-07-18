package com.sentinel.host.data.remote

import com.sentinel.host.domain.model.ReconnectConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReconnectPolicyTest {

    @Test
    fun `first attempt has initial delay`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(initialDelayMs = 1000, jitterFactor = 0.0),
            random = Random(42)
        )
        assertEquals(1000L, policy.getDelayMs(0))
    }

    @Test
    fun `delay doubles each attempt`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 60_000,
                multiplier = 2.0,
                jitterFactor = 0.0
            ),
            random = Random(42)
        )
        assertEquals(1000L, policy.getDelayMs(0))  // 1000 * 2^0
        assertEquals(2000L, policy.getDelayMs(1))  // 1000 * 2^1
        assertEquals(4000L, policy.getDelayMs(2))  // 1000 * 2^2
        assertEquals(8000L, policy.getDelayMs(3))  // 1000 * 2^3
        assertEquals(16000L, policy.getDelayMs(4)) // 1000 * 2^4
    }

    @Test
    fun `delay is capped at maxDelay`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 5000,
                multiplier = 2.0,
                jitterFactor = 0.0
            ),
            random = Random(42)
        )
        assertEquals(5000L, policy.getDelayMs(10)) // Would be 1024000 without cap
    }

    @Test
    fun `jitter varies delay within range`() {
        val config = ReconnectConfig(
            initialDelayMs = 10_000,
            jitterFactor = 0.2
        )
        val delays = (1..100).map {
            val policy = ReconnectPolicy(config, Random(it))
            policy.getDelayMs(0)
        }.toSet()

        // With 20% jitter on 10000ms, range is 8000-12000
        assertTrue(delays.size > 1) // Not all the same
        assertTrue(delays.all { it in 7_000..13_000 })
    }

    @Test
    fun `shouldRetry returns true within limit`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(maxAttempts = 5)
        )
        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(4))
    }

    @Test
    fun `shouldRetry returns false at limit`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(maxAttempts = 5)
        )
        assertFalse(policy.shouldRetry(5))
        assertFalse(policy.shouldRetry(10))
    }

    @Test
    fun `shouldRetry with unlimited attempts always returns true`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(maxAttempts = 0)
        )
        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(100))
        assertTrue(policy.shouldRetry(999))
    }

    @Test
    fun `delay is never negative`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(
                initialDelayMs = 100,
                jitterFactor = 0.9 // Extreme jitter
            )
        )
        repeat(1000) {
            assertTrue(policy.getDelayMs(0) >= 0)
        }
    }

    @Test
    fun `maxAttempts property matches config`() {
        val policy = ReconnectPolicy(
            config = ReconnectConfig(maxAttempts = 7)
        )
        assertEquals(7, policy.maxAttempts)
    }
}
