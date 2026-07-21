package com.sentinel.host.domain.location

import com.sentinel.host.domain.model.LocationConfig
import com.sentinel.host.domain.model.LocationUpdate
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over platform location services.
 * Implementations wrap FusedLocationProviderClient (or test fakes).
 *
 * Lifecycle:
 * - [startUpdates] begins location observation with given config.
 * - [stopUpdates] cancels the active location request.
 * - [locations] emits updates as they arrive.
 *
 * Permission checking is the caller's responsibility.
 */
interface LocationProvider {
    /** Flow of location updates. Active only while a request is running. */
    val locations: Flow<LocationUpdate>

    /** Whether updates are currently active. */
    val isActive: Boolean

    /** Starts location updates with the given configuration. */
    fun startUpdates(config: LocationConfig)

    /** Stops location updates and releases resources. */
    fun stopUpdates()

    /** Last known location, or null if none available. */
    val lastLocation: LocationUpdate?

    /** Returns true if location services (GPS/Network) are enabled on the device. */
    fun isLocationEnabled(): Boolean
}
