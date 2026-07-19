package com.sentinel.host.domain.model

/**
 * Configuration for location updates.
 * Battery-aware defaults: 15s interval, 10m distance, balanced priority.
 */
data class LocationConfig(
    /** Desired interval between updates in milliseconds. */
    val intervalMs: Long = 15_000L,

    /** Fastest interval the app can handle in milliseconds. */
    val fastestIntervalMs: Long = 5_000L,

    /** Minimum displacement in meters to trigger an update. */
    val minDistanceMeters: Float = 0f,

    /**
     * Location priority:
     * - PRIORITY_HIGH_ACCURACY = 100
     * - PRIORITY_BALANCED_POWER_ACCURACY = 102
     * - PRIORITY_LOW_POWER = 104
     * - PRIORITY_PASSIVE = 105
     */
    val priority: Int = 100
)
