package com.sentinel.host.service

import android.util.Log
import com.sentinel.host.domain.location.LocationProvider
import com.sentinel.host.domain.model.LocationConfig
import com.sentinel.host.domain.model.LocationUpdate
import com.sentinel.host.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Orchestrates location streaming tied to the connection lifecycle.
 *
 * Lifecycle:
 * - [start] when ConnectionState becomes Ready.
 * - [stop] on disconnect, reconnect, or user stop.
 * - [pause] during reconnect — stops provider but preserves config.
 * - [resume] after reconnect succeeds — restarts provider.
 *
 * Permission handling:
 * - Caller must check location permissions before calling [start].
 * - If [hasPermission] is false, [start] is a no-op.
 *
 * Battery impact:
 * - Configurable interval (default 15s) and min distance (10m).
 * - Balanced power accuracy by default.
 * - No updates while disconnected or reconnecting.
 *
 * Duplicate prevention:
 * - [start] stops any existing collection before starting new one.
 */
class LocationStreamer(
    private val locationProvider: LocationProvider,
    private val locationRepository: LocationRepository,
    private val scope: CoroutineScope,
    private val config: LocationConfig = LocationConfig()
) {

    companion object {
        private const val TAG = "Sentinel:LocationStream"
    }

    private var collectJob: Job? = null

    /** Whether location permission has been granted. Set by the UI/permission layer. */
    @Volatile
    var hasPermission: Boolean = false

    /** Whether location services are enabled on the device. */
    val isLocationEnabled: Boolean get() = locationProvider.isLocationEnabled()

    /** Whether the streamer is actively collecting and sending locations. */
    val isStreaming: Boolean get() = collectJob?.isActive == true && locationProvider.isActive

    /** Last successfully collected location. */
    val lastLocation: LocationUpdate? get() = locationProvider.lastLocation

    /**
     * Starts location updates and begins collecting/sending.
     * No-op if [hasPermission] is false.
     */
    fun start() {
        if (!hasPermission) {
            Log.w(TAG, "No location permission — skipping start")
            return
        }

        stop() // Prevent duplicates

        locationProvider.startUpdates(config)

        collectJob = locationProvider.locations
            .onEach { update -> sendLocation(update) }
            .launchIn(scope)

        Log.i(TAG, "Location streaming started")
    }

    /**
     * Stops location updates and collection.
     */
    fun stop() {
        collectJob?.cancel()
        collectJob = null
        locationProvider.stopUpdates()
        Log.i(TAG, "Location streaming stopped")
    }

    /**
     * Pauses location updates during reconnect.
     * Same as [stop] but semantically different for logging.
     */
    fun pause() {
        collectJob?.cancel()
        collectJob = null
        locationProvider.stopUpdates()
        Log.i(TAG, "Location streaming paused (reconnecting)")
    }

    /**
     * Resumes location updates after reconnect.
     * Same as [start] but semantically different for logging.
     */
    fun resume() {
        if (!hasPermission) {
            Log.w(TAG, "No location permission — skipping resume")
            return
        }

        stop() // Clean up any lingering state

        locationProvider.startUpdates(config)

        collectJob = locationProvider.locations
            .onEach { update -> sendLocation(update) }
            .launchIn(scope)

        Log.i(TAG, "Location streaming resumed")
    }

    private suspend fun sendLocation(update: LocationUpdate) {
        try {
            locationRepository.sendLocation(update)
            Log.d(TAG, "Location sent: ${update.latitude}, ${update.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send location: ${e.message}")
        }
    }
}
