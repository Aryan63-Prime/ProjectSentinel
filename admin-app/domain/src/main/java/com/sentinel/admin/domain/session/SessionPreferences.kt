package com.sentinel.admin.domain.session

/**
 * Session preferences for login/session management.
 *
 * Stores server URL and Remember Me toggle.
 * Token is managed separately by [AuthRepository].
 */
interface SessionPreferences {
    var serverUrl: String?
    var rememberMe: Boolean
    fun clear()
}
