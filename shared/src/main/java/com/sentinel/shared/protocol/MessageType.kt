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

    // File operations
    const val FILES_LIST_REQ = "FILES_LIST_REQ"
    const val FILES_LIST_RES = "FILES_LIST_RES"
    const val FILE_DOWNLOAD_REQ = "FILE_DOWNLOAD_REQ"
    const val FILE_DOWNLOAD_RES = "FILE_DOWNLOAD_RES"
    const val FILE_CHUNK_ACK = "FILE_CHUNK_ACK"
    const val FILE_STOP_REQ = "FILE_STOP_REQ"
    // Air Commands
    const val COMMAND = "COMMAND"
    const val COMMAND_RESULT = "COMMAND_RESULT"

    // Batch & Telemetry
    const val LOCATION_BATCH = "LOCATION_BATCH"
    const val CRASH_REPORT = "CRASH_REPORT"
    const val EMERGENCY_SOS = "EMERGENCY_SOS"
    const val GEOFENCE_TRANSITION = "GEOFENCE_TRANSITION"

    // PTT Intercom
    const val PTT_START = "PTT_START"
    const val PTT_STOP = "PTT_STOP"
    const val PTT_AUDIO = "PTT_AUDIO"
}
