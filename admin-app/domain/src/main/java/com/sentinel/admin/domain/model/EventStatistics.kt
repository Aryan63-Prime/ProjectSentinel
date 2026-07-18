package com.sentinel.admin.domain.model

/**
 * Immutable debug statistics for live WebSocket event processing.
 *
 * Exposed as a StateFlow from DeviceRepository for monitoring.
 */
data class EventStatistics(
    val received: Long = 0,
    val applied: Long = 0,
    val ignored: Long = 0,
    val duplicates: Long = 0,
    val stale: Long = 0,
    val reconnectResyncs: Long = 0
)
