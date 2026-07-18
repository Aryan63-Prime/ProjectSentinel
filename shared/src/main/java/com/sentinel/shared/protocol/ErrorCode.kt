package com.sentinel.shared.protocol

/**
 * Error codes per PROTOCOL.md.
 * Matches the server's error response codes.
 */
object ErrorCode {
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val ALREADY_REGISTERED = 409
    const val INTERNAL_ERROR = 500
}
