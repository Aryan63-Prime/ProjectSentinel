package com.sentinel.admin.domain.model

/**
 * Events received during the connection lifecycle.
 *
 * The supervisor consumes events and derives state.
 * Supervisor → Events → State (not Supervisor → State → Guess).
 */
sealed interface ConnectionEvent {
    /** WebSocket transport connected. */
    data object Connected : ConnectionEvent

    /** Server acknowledged authentication. */
    data class AuthResult(val success: Boolean, val error: String? = null) : ConnectionEvent

    /** Server sent a heartbeat acknowledgement. */
    data object HeartbeatAck : ConnectionEvent

    /** Server sent an error message. */
    data class ServerError(val code: Int, val message: String) : ConnectionEvent

    /** WebSocket transport disconnected. */
    data object Disconnected : ConnectionEvent

    /** Binary audio frame received from server. */
    data class AudioFrameReceived(val data: ByteArray) : ConnectionEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioFrameReceived) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /** Binary file chunk received from server. */
    data class FileChunkReceived(val data: ByteArray) : ConnectionEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FileChunkReceived) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    // Reconnection events
    data class Reconnecting(val attempt: Int, val delayMs: Long) : ConnectionEvent
    data class ReconnectFailed(val attempt: Int, val reason: String) : ConnectionEvent
    data object ReconnectExhausted : ConnectionEvent

    /** Server pushed a DEVICE_UPDATE message with device state changes. */
    data class DeviceUpdateReceived(val rawJson: String) : ConnectionEvent

    /** File list response received. */
    data class FilesListReceived(val rawJson: String) : ConnectionEvent

    /** File download response received. */
    data class FileDownloadReceived(val rawJson: String) : ConnectionEvent
}
