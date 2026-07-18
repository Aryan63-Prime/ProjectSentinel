package com.sentinel.host.data.remote.protocol

import com.squareup.moshi.JsonClass

/**
 * Moshi-serializable protocol models.
 * These are internal transport types — never exposed to the domain layer.
 *
 * Outgoing payloads: serialized into the "data" field of the protocol envelope.
 * Incoming models: full envelope + typed data for deserialization.
 */

// ============================================================
// Envelope — used to extract "type" from any incoming message
// ============================================================

@JsonClass(generateAdapter = true)
internal data class EnvelopeJson(
    val type: String = "",
    val version: Int = 0,
    val timestamp: Long = 0,
    val sequence: Long = 0
)

// ============================================================
// Outgoing data payloads
// ============================================================

@JsonClass(generateAdapter = true)
internal data class AuthDataJson(
    val token: String
)

@JsonClass(generateAdapter = true)
internal data class RegisterDataJson(
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val model: String
)

@JsonClass(generateAdapter = true)
internal data class LocationDataJson(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val battery: Int,
    val network: String
)

// ============================================================
// Incoming full messages (envelope + typed data)
// ============================================================

@JsonClass(generateAdapter = true)
internal data class AckDataJson(
    val success: Boolean = false
)

@JsonClass(generateAdapter = true)
internal data class AckMessageJson(
    val type: String = "",
    val version: Int = 0,
    val timestamp: Long = 0,
    val sequence: Long = 0,
    val data: AckDataJson = AckDataJson()
)

@JsonClass(generateAdapter = true)
internal data class ErrorDataJson(
    val code: Int = 0,
    val message: String = ""
)

@JsonClass(generateAdapter = true)
internal data class ErrorMessageJson(
    val type: String = "",
    val version: Int = 0,
    val timestamp: Long = 0,
    val sequence: Long = 0,
    val data: ErrorDataJson = ErrorDataJson()
)
