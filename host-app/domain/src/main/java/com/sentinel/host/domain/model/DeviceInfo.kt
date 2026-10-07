package com.sentinel.host.domain.model

/**
 * Identifies this device to the server.
 * DeviceID is permanent and survives reconnects.
 * Includes hardware-level telemetry and identity properties.
 */
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val model: String,
    val hardwareId: String = "",
    val hardwareBrand: String = "",
    val hardwareBoard: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val callsign: String = "",
    val fcmToken: String = ""
)
