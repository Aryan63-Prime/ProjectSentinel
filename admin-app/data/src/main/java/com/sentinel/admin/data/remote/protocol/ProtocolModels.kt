package com.sentinel.admin.data.remote.protocol

import com.squareup.moshi.JsonClass

/**
 * Moshi-serializable protocol models.
 * Internal transport types — never exposed to the domain layer.
 *
 * Admin-specific:
 * - No RegisterDataJson (Admin doesn't register as device)
 * - No LocationDataJson (Admin doesn't publish location)
 * - Adds ListenDataJson and StopDataJson for audio control
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
internal data class ListenDataJson(
    val deviceId: String
)

@JsonClass(generateAdapter = true)
internal data class StopDataJson(
    val deviceId: String
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
