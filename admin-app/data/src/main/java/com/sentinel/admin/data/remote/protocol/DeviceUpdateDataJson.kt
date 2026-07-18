package com.sentinel.admin.data.remote.protocol

import com.squareup.moshi.JsonClass

/**
 * Moshi model for the DEVICE_UPDATE message data payload.
 *
 * Only changed fields are populated by the server.
 * Null fields mean "no change" — not "clear the value".
 */
@JsonClass(generateAdapter = true)
data class DeviceUpdateDataJson(
    val event: String = "",
    val deviceId: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val network: String? = null,
    val timestamp: String? = null,
    val deviceName: String? = null,
    val appVersion: String? = null,
    val model: String? = null
)

/**
 * Full DEVICE_UPDATE message including envelope and data.
 */
@JsonClass(generateAdapter = true)
internal data class DeviceUpdateMessageJson(
    val type: String = "",
    val version: Int = 0,
    val timestamp: Long = 0,
    val sequence: Long = 0,
    val data: DeviceUpdateDataJson = DeviceUpdateDataJson()
)
