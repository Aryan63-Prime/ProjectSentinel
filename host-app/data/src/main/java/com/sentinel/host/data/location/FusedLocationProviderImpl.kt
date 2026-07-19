package com.sentinel.host.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sentinel.host.domain.location.LocationProvider
import com.sentinel.host.domain.model.LocationConfig
import com.sentinel.host.domain.model.LocationUpdate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Wraps [FusedLocationProviderClient] behind the [LocationProvider] interface.
 *
 * Permission checking is the caller's responsibility — this class uses
 * @SuppressLint("MissingPermission") because the caller must verify
 * location permissions before calling [startUpdates].
 *
 * Battery impact:
 * - Uses configurable interval (default 15s)
 * - Minimum displacement filter (default 10m)
 * - Balanced power accuracy by default
 * - No wake locks — uses Looper callback
 */
class FusedLocationProviderImpl(context: Context) : LocationProvider {

    companion object {
        private const val TAG = "Sentinel:Location"
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locations = MutableSharedFlow<LocationUpdate>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val locations: Flow<LocationUpdate> = _locations.asSharedFlow()

    @Volatile
    override var isActive: Boolean = false
        private set

    @Volatile
    private var _lastLocation: LocationUpdate? = null
    override val lastLocation: LocationUpdate? get() = _lastLocation

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val update = LocationUpdate(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                battery = 0, // Filled by caller
                network = "" // Filled by caller
            )
            _lastLocation = update
            _locations.tryEmit(update)
            Log.d(TAG, "Location: ${location.latitude}, ${location.longitude} (acc=${location.accuracy}m)")
        }
    }

    @SuppressLint("MissingPermission")
    override fun startUpdates(config: LocationConfig) {
        if (isActive) {
            Log.d(TAG, "Already active — stopping first")
            stopUpdates()
        }

        val request = LocationRequest.Builder(config.intervalMs)
            .setMinUpdateIntervalMillis(config.fastestIntervalMs)
            .setMinUpdateDistanceMeters(config.minDistanceMeters)
            .setPriority(mapPriority(config.priority))
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isActive = true
        Log.i(TAG, "Location updates started (interval=${config.intervalMs}ms, distance=${config.minDistanceMeters}m)")

        // Immediately emit last known location as a fallback while waiting for GPS fix
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val update = LocationUpdate(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    battery = 0,
                    network = ""
                )
                _lastLocation = update
                _locations.tryEmit(update)
                Log.i(TAG, "Last known location: ${location.latitude}, ${location.longitude}")
            } else {
                Log.w(TAG, "No last known location available")
            }
        }
    }

    override fun stopUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
        isActive = false
        Log.i(TAG, "Location updates stopped")
    }

    private fun mapPriority(priority: Int): Int {
        return when (priority) {
            100 -> Priority.PRIORITY_HIGH_ACCURACY
            102 -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            104 -> Priority.PRIORITY_LOW_POWER
            105 -> Priority.PRIORITY_PASSIVE
            else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
    }
}
