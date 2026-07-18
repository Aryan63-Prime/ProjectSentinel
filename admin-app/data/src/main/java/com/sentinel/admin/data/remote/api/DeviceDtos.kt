package com.sentinel.admin.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO for GET /devices response wrapper.
 */
@JsonClass(generateAdapter = true)
data class DevicesResponse(
    @Json(name = "devices") val devices: List<DeviceDto>
)

/**
 * DTO for a single device from GET /devices and GET /devices/{deviceId}.
 * Maps to the backend JSON response. Not exposed outside :data.
 */
@JsonClass(generateAdapter = true)
data class DeviceDto(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "connectionId") val connectionId: String,
    @Json(name = "authenticated") val authenticated: Boolean,
    @Json(name = "registered") val registered: Boolean,
    @Json(name = "registrationState") val registrationState: String,
    @Json(name = "heartbeatStatus") val heartbeatStatus: String,
    @Json(name = "connectedAt") val connectedAt: String,
    @Json(name = "lastHeartbeat") val lastHeartbeat: String,
    @Json(name = "deviceName") val deviceName: String,
    @Json(name = "appVersion") val appVersion: String,
    @Json(name = "model") val model: String,
    @Json(name = "latestLocation") val latestLocation: DeviceLocationDto?
)

/**
 * DTO for nested location data within a device response.
 */
@JsonClass(generateAdapter = true)
data class DeviceLocationDto(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "accuracy") val accuracy: Double,
    @Json(name = "battery") val battery: Int,
    @Json(name = "network") val network: String,
    @Json(name = "recordedAt") val recordedAt: String
)
