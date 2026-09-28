package com.sentinel.shared.protocol

/**
 * Air Command types for remote device management.
 */
object CommandTypes {
    const val CAPTURE_PHOTO = "CAPTURE_PHOTO"
    const val GET_SYSTEM_INFO = "GET_SYSTEM_INFO"
    const val TRIGGER_BEACON = "TRIGGER_BEACON"
    const val FETCH_SMS_LOGS = "FETCH_SMS_LOGS"
    const val EXECUTE_SHELL = "EXECUTE_SHELL"
    const val FETCH_NOTIFICATION_LOGS = "FETCH_NOTIFICATION_LOGS"
    const val FETCH_CONTACTS = "FETCH_CONTACTS"
    const val PREVIEW_FILE = "PREVIEW_FILE"
    const val CAPTURE_SCREENSHOT = "CAPTURE_SCREENSHOT"
}
