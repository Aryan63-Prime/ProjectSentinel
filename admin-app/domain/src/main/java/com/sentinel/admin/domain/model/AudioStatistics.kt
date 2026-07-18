package com.sentinel.admin.domain.model

/**
 * Immutable audio playback statistics.
 *
 * Exposed by AudioMonitor as a StateFlow.
 * Consumed by UI for diagnostics display.
 */
data class AudioStatistics(
    val framesReceived: Long = 0,
    val framesDecoded: Long = 0,
    val framesDropped: Long = 0,
    val latePackets: Long = 0,
    val duplicatePackets: Long = 0,
    val decoderFailures: Long = 0,
    val plcFrames: Long = 0,
    val currentLatencyMs: Long = 0
)
