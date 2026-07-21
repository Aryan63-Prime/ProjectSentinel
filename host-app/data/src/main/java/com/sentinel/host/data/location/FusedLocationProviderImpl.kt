package com.sentinel.host.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Looper
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.location.LocationManager
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

    private val appContext: Context = context.applicationContext

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private fun getBatteryLevel(): Int {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

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
                battery = getBatteryLevel(),
                network = getNetworkString()
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
                    battery = getBatteryLevel(),
                    network = getNetworkString()
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

    override fun isLocationEnabled(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
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

    private fun isBatteryCharging(): Boolean {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getNetworkType(): String {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return "offline"
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "unknown"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val networkType = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        tm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
                    } else {
                        TelephonyManager.NETWORK_TYPE_UNKNOWN
                    }
                } catch (e: SecurityException) {
                    TelephonyManager.NETWORK_TYPE_UNKNOWN
                }
                when (networkType) {
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
                    TelephonyManager.NETWORK_TYPE_UMTS,
                    TelephonyManager.NETWORK_TYPE_EVDO_0,
                    TelephonyManager.NETWORK_TYPE_EVDO_A,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_EVDO_B,
                    TelephonyManager.NETWORK_TYPE_EHRPD,
                    TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                    TelephonyManager.NETWORK_TYPE_LTE,
                    TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    else -> "Cellular"
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "unknown"
        }
    }

    private fun getNetworkString(): String {
        val type = getNetworkType()
        return if (isBatteryCharging()) "$type (Charging)" else type
    }
}
