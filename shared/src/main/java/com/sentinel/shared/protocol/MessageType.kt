package com.sentinel.shared.protocol

/**
 * Control message types per PROTOCOL.md.
 * These values are used in the "type" field of every JSON control message.
 */
object MessageType {
    const val AUTH = "AUTH"
    const val AUTH_ACK = "AUTH_ACK"
    const val REGISTER = "REGISTER"
    const val REGISTER_ACK = "REGISTER_ACK"
    const val HEARTBEAT = "HEARTBEAT"
    const val HEARTBEAT_ACK = "HEARTBEAT_ACK"
    const val LOCATION = "LOCATION"
    const val LISTEN = "LISTEN"
    const val STOP = "STOP"
    const val PING = "PING"
    const val PONG = "PONG"
    const val ERROR = "ERROR"
    const val DEVICE_UPDATE = "DEVICE_UPDATE"
}
