package com.sentinel.admin.ui.login

import com.sentinel.admin.domain.model.ConnectionState

/**
 * Immutable UI state for the Login screen.
 *
 * The ViewModel produces new instances of this state.
 * The Compose UI observes and recomposes on changes.
 */
data class LoginUiState(
    /** Server WebSocket URL. */
    val serverUrl: String = "",
    /** JWT authentication token. */
    val token: String = "",
    /** Whether to persist credentials across restarts. */
    val rememberMe: Boolean = false,
    /** Current connection state from the supervisor. */
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** Error message to display, null if no error. */
    val errorMessage: String? = null,
    /** Whether a connection attempt is in progress. */
    val isConnecting: Boolean = false,
    /** Input validation error for server URL. */
    val serverUrlError: String? = null,
    /** Input validation error for token. */
    val tokenError: String? = null
) {
    /** True if the connect button should be enabled. */
    val canConnect: Boolean
        get() = !isConnecting &&
                serverUrl.isNotBlank() &&
                token.isNotBlank() &&
                (connectionState is ConnectionState.Disconnected ||
                        connectionState is ConnectionState.Error)

    /** True if the disconnect button should be visible. */
    val canDisconnect: Boolean
        get() = connectionState !is ConnectionState.Disconnected

    /** True if inputs should be editable. */
    val inputsEnabled: Boolean
        get() = connectionState is ConnectionState.Disconnected ||
                connectionState is ConnectionState.Error

    /** Status text for the connection state banner. */
    val statusText: String
        get() = when (connectionState) {
            is ConnectionState.Disconnected -> "Disconnected"
            is ConnectionState.Connecting -> "Connecting…"
            is ConnectionState.TransportConnected -> "Transport connected…"
            is ConnectionState.Authenticating -> "Authenticating…"
            is ConnectionState.Authenticated -> "Authenticated"
            is ConnectionState.Ready -> "Connected"
            is ConnectionState.Reconnecting -> "Reconnecting (attempt ${connectionState.attempt})…"
            is ConnectionState.Error -> "Error: ${connectionState.message}"
        }
}
