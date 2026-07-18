package com.sentinel.admin.data.remote.protocol

/**
 * Deserialized incoming control message from the server.
 * Each variant maps to a specific protocol message type.
 *
 * Admin-specific: no RegisterAck (Admin doesn't register as device).
 */
sealed interface IncomingMessage {
    val type: String
    val sequence: Long

    data class AuthAck(
        override val type: String,
        override val sequence: Long,
        val success: Boolean
    ) : IncomingMessage

    data class HeartbeatAck(
        override val type: String,
        override val sequence: Long
    ) : IncomingMessage

    data class Pong(
        override val type: String,
        override val sequence: Long
    ) : IncomingMessage

    data class Error(
        override val type: String,
        override val sequence: Long,
        val code: Int,
        val message: String
    ) : IncomingMessage

    data class DeviceUpdate(
        override val type: String,
        override val sequence: Long,
        val updateData: DeviceUpdateDataJson
    ) : IncomingMessage

    data class Unknown(
        override val type: String,
        override val sequence: Long
    ) : IncomingMessage
}
