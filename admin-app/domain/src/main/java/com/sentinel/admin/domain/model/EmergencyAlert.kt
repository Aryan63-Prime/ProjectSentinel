package com.sentinel.admin.domain.model

import java.util.UUID

/**
 * Domain model representing an active Emergency Alert (e.g. Fall Detection)
 * received from a fleet device.
 */
data class EmergencyAlert(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val callsign: String,
    val model: String,
    val triggerReason: String = "FALL_DETECTED",
    val impactGForce: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
