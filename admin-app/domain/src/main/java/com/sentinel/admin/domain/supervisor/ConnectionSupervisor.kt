package com.sentinel.admin.domain.supervisor

import com.sentinel.admin.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for the admin connection supervisor.
 *
 * The ViewModel depends on this interface, not the concrete
 * implementation, enabling testing with fakes.
 */
interface ConnectionSupervisor {
    /** Observable connection state. */
    val connectionState: StateFlow<ConnectionState>

    /** Starts connection to the given server URL. */
    fun start(serverUrl: String)

    /** Disconnects and stops all monitoring. */
    fun stop()

    /**
     * Ensures the supervisor is connected. If it was previously started
     * but is currently disconnected, triggers a reconnect using the last
     * known server URL. No-op if already connected or if start() was never called.
     */
    fun ensureConnected()
}
