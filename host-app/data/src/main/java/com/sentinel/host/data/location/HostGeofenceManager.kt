package com.sentinel.host.data.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.sentinel.host.data.receiver.GeofenceBroadcastReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GeofenceZone(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val expirationDurationMs: Long = Geofence.NEVER_EXPIRE
)

@Singleton
class HostGeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:GeofenceManager"
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun registerZones(zones: List<GeofenceZone>, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (zones.isEmpty()) return

        val geofenceList = zones.map { zone ->
            Geofence.Builder()
                .setRequestId(zone.id)
                .setCircularRegion(zone.latitude, zone.longitude, zone.radiusMeters)
                .setExpirationDuration(zone.expirationDurationMs)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofenceList)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent).run {
            addOnSuccessListener {
                Log.i(TAG, "Successfully registered ${zones.size} hardware-assisted geofences")
                onSuccess()
            }
            addOnFailureListener { e ->
                Log.e(TAG, "Failed to register geofences: ${e.message}", e)
                onError(e.message ?: "Unknown geofence registration error")
            }
        }
    }

    fun clearAllGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent).addOnCompleteListener {
            Log.i(TAG, "All active geofences removed")
        }
    }
}
