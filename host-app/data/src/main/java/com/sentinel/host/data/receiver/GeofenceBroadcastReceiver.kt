package com.sentinel.host.data.receiver

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
        
        // Listener callback for active background service / WebSocket dispatcher
        var onTransitionListener: ((String) -> Unit)? = null
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

            onTransitionListener?.invoke(eventJson.toString())
        }
    }
}
