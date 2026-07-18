package com.sentinel.admin.domain.model

/**
 * Configuration for exponential backoff with jitter.
 * Pure Kotlin — no Android dependency.
 */
data class ReconnectConfig(
    /** Initial delay before first retry (ms). */
    val initialDelayMs: Long = 1_000L,
    /** Maximum delay cap (ms). */
    val maxDelayMs: Long = 30_000L,
    /** Maximum number of retry attempts. 0 = unlimited. */
    val maxAttempts: Int = 10,
    /** Backoff multiplier per attempt. */
    val multiplier: Double = 2.0,
    /** Jitter factor (0.0–1.0). Applied as ±jitter% of computed delay. */
    val jitterFactor: Double = 0.2
)
