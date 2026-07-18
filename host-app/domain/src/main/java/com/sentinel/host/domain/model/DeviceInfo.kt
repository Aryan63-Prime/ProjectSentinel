package com.sentinel.host.domain.model

/**
 * Identifies this device to the server.
 * DeviceID is permanent and survives reconnects.
 */
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val model: String
)
