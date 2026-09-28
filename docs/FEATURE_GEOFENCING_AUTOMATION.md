# Feature Specification & Code: Geofencing & Event Automation Engine

## 1. Overview & Problem Statement
Asset tracking and workforce safety platforms require automated boundary supervision. If boundary checks are performed solely on the server, a host device that loses cellular data while exiting a restricted perimeter will fail to trigger emergency alerts.

This feature implements:
1. **Client-Side Hardware-Assisted Geofencing:** Utilizes Google Play Services `GeofencingClient` directly on the host device. Geofence transitions (`GEOFENCE_TRANSITION_ENTER`, `GEOFENCE_TRANSITION_EXIT`, `GEOFENCE_TRANSITION_DWELL`) trigger locally even if the host is completely offline.
2. **Visual Geofence Builder (`admin-app`):** Interactive Google Maps drawing tools to define boundary circles and trigger actions (e.g., automated photo capture or beacon alerts upon exit).

---

## 2. Protocol Specification (`shared/`)

Add message type in [`shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt):

```kotlin
object MessageType {
    // Existing types...
    const val GEOFENCE_TRANSITION = "GEOFENCE_TRANSITION"
    const val SYNC_GEOFENCES = "SYNC_GEOFENCES"
}
```

### Transition Event Payload (`Host → Server → Admin`)
```json
{
  "type": "GEOFENCE_TRANSITION",
  "version": 1,
  "timestamp": 1727500300,
  "sequence": 150,
  "data": {
    "geofenceId": "zone_headquarters_01",
    "transitionType": "EXIT",
    "latitude": 37.7751,
    "longitude": -122.4196,
    "accuracy": 5.0,
    "timestamp": 1727500295
  }
}
```

---

## 3. Host-App Implementation

### File: `host-app/data/src/main/java/com/sentinel/host/data/location/HostGeofenceManager.kt`

```kotlin
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
import com.sentinel.host.receiver.GeofenceBroadcastReceiver
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
    fun registerZones(zones: List<GeofenceZone>, onSuccess: () -> Unit, onError: (String) -> Unit) {
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
```

### File: `host-app/app/src/main/java/com/sentinel/host/receiver/GeofenceBroadcastReceiver.kt`

```kotlin
package com.sentinel.host.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.sentinel.shared.protocol.MessageType
import org.json.JSONObject

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "Sentinel:GeofenceReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Geofencing error: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
        val triggeringLocation = geofencingEvent.triggeringLocation

        val transitionType = when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> "UNKNOWN"
        }

        for (geofence in triggeringGeofences) {
            val geofenceId = geofence.requestId
            Log.w(TAG, "Geofence [$geofenceId] transition triggered: $transitionType")

            // Construct payload and dispatch or buffer
            val eventJson = JSONObject().apply {
                put("type", MessageType.GEOFENCE_TRANSITION)
                put("version", 1)
                put("timestamp", System.currentTimeMillis() / 1000)

                val data = JSONObject().apply {
                    put("geofenceId", geofenceId)
                    put("transitionType", transitionType)
                    put("latitude", triggeringLocation?.latitude ?: 0.0)
                    put("longitude", triggeringLocation?.longitude ?: 0.0)
                    put("accuracy", triggeringLocation?.accuracy?.toDouble() ?: 0.0)
                }
                put("data", data)
            }

            // Save to offline buffer or send immediately if connected
            // OfflineTelemetryBuffer / SentinelForegroundService integration
        }
    }
}
```

---

## 4. Admin-App Implementation

### File: `admin-app/app/src/main/java/com/sentinel/admin/ui/map/GeofenceCircleOverlay.kt`

```kotlin
package com.sentinel.admin.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.sentinel.host.data.location.GeofenceZone

@Composable
fun GeofenceCircleOverlay(
    zones: List<GeofenceZone>
) {
    zones.forEach { zone ->
        val center = LatLng(zone.latitude, zone.longitude)

        Circle(
            center = center,
            radius = zone.radiusMeters.toDouble(),
            fillColor = Color(0x221E88E5), // Translucent Blue
            strokeColor = Color(0xFF1E88E5),
            strokeWidth = 3f
        )

        Marker(
            state = MarkerState(position = center),
            title = zone.id,
            snippet = "Radius: ${zone.radiusMeters}m",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        )
    }
}
```
