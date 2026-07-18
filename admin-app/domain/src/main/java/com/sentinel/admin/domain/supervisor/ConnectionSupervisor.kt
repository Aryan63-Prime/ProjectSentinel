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
}
