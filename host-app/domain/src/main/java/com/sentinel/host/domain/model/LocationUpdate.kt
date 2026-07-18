package com.sentinel.host.domain.model

/**
 * GPS location enriched with battery and network state.
 * Matches the server's LocationMessage payload.
 */
data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val battery: Int,
    val network: String
)
