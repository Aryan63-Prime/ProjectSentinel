package com.sentinel.host.data.remote.protocol

/**
 * Deserialized incoming control message from the server.
 * Each variant maps to a specific protocol message type.
 */
sealed interface IncomingMessage {
    val type: String
    val sequence: Long

    data class AuthAck(
        override val type: String,
        override val sequence: Long,
        val success: Boolean
    ) : IncomingMessage

    data class RegisterAck(
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

    data class Unknown(
        override val type: String,
        override val sequence: Long
    ) : IncomingMessage
}
