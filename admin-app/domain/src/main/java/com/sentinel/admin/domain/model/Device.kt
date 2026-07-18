package com.sentinel.admin.domain.model

/**
 * A monitored device as returned by the Admin REST API.
 *
 * Maps to the JSON response from GET /devices and GET /devices/{deviceId}.
 *
 * @property deviceId Permanent device identifier (e.g., "HOST-0001").
 * @property connectionId Ephemeral connection identifier (changes on reconnect).
 * @property authenticated Whether the device has completed AUTH.
 * @property registered Whether the device has completed REGISTER.
 * @property registrationState Human-readable registration state.
 * @property heartbeatStatus "online" or "offline".
 * @property connectedAt ISO-8601 timestamp of when the device connected.
 * @property lastHeartbeat ISO-8601 timestamp of last heartbeat.
 * @property deviceName User-friendly device name (e.g., "Pixel 9").
 * @property appVersion Host application version.
 * @property model Device hardware model.
 * @property latestLocation Most recent location, or null.
 */
data class Device(
    val deviceId: String,
    val connectionId: String,
    val authenticated: Boolean,
    val registered: Boolean,
    val registrationState: String,
    val heartbeatStatus: String,
    val connectedAt: String,
    val lastHeartbeat: String,
    val deviceName: String,
    val appVersion: String,
    val model: String,
    val latestLocation: DeviceLocation?
)
