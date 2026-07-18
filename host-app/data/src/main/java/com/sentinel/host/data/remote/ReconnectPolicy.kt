package com.sentinel.host.data.remote

import com.sentinel.host.domain.model.ReconnectConfig
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Computes reconnect delays using exponential backoff with jitter.
 *
 * Formula: min(initialDelay * multiplier^attempt, maxDelay) ± jitter
 *
 * Thread-safe — all functions are pure.
 */
class ReconnectPolicy(
    private val config: ReconnectConfig = ReconnectConfig(),
    private val random: Random = Random.Default
) {

    /**
     * Computes delay for the given attempt (0-indexed).
     * Returns delay in milliseconds with jitter applied.
     */
    fun getDelayMs(attempt: Int): Long {
        val exponentialDelay = config.initialDelayMs * config.multiplier.pow(attempt.toDouble())
        val cappedDelay = min(exponentialDelay.toLong(), config.maxDelayMs)
        if (config.jitterFactor == 0.0) return cappedDelay
        val jitter = (random.nextDouble(-config.jitterFactor, config.jitterFactor) * cappedDelay).toLong()
        return (cappedDelay + jitter).coerceAtLeast(0)
    }

    /**
     * Returns true if the attempt number is within the retry limit.
     * Attempt is 0-indexed (0 = first retry).
     */
    fun shouldRetry(attempt: Int): Boolean {
        if (config.maxAttempts <= 0) return true // unlimited
        return attempt < config.maxAttempts
    }

    /**
     * Returns the maximum number of attempts configured.
     */
    val maxAttempts: Int get() = config.maxAttempts
}
