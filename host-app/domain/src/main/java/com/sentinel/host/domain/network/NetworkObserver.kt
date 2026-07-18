package com.sentinel.host.domain.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Observes network connectivity state.
 * Interface in :domain so :service can depend on it without Android coupling.
 * Implementation wraps ConnectivityManager.
 */
interface NetworkObserver {
    /** True when any network is available. */
    val isAvailable: StateFlow<Boolean>

    fun start()
    fun stop()
}
