package com.sentinel.host.service

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sentinel.host.domain.session.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Firebase Cloud Messaging Service for Sentinel Host.
 *
 * Responsibilities:
 * 1. Receives and persists device registration tokens from Google Play Services.
 * 2. Catches high-priority FCM data pushes and immediately wakes the device from
 *    deep Android Doze mode using a temporary partial WakeLock.
 * 3. Restarts or signals [SentinelForegroundService] to reconnect the WebSocket uplink.
 */
@AndroidEntryPoint
class SentinelFcmService : FirebaseMessagingService() {

    @Inject lateinit var sessionManager: SessionManager

    companion object {
        private const val TAG = "Sentinel:FCM"
        const val ACTION_WAKE_UP = "WAKE_UP"
        const val ACTION_RECONNECT = "RECONNECT"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New Firebase FCM registration token generated: $token")
        try {
            sessionManager.saveFcmToken(token)
            Log.i(TAG, "Saved FCM token into SessionManager successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist FCM token: ${e.message}", e)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val action = data["action"] ?: ACTION_WAKE_UP
        val targetDevice = data["deviceId"] ?: "unknown"
        val timestamp = data["timestamp"] ?: System.currentTimeMillis().toString()

        Log.i(TAG, "High-priority FCM push received: action=$action, target=$targetDevice, time=$timestamp")

        // Acquire a temporary partial wake lock (max 15 seconds) to ensure CPU does not sleep
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Sentinel:FcmWakeUpWakeLock"
        )
        wakeLock?.acquire(15_000L)

        try {
            when (action.uppercase()) {
                ACTION_WAKE_UP, "WAKE", ACTION_RECONNECT, "PING" -> {
                    Log.i(TAG, "FCM: Awakening Sentinel Foreground Service and restoring network connection...")

                    val serviceIntent = Intent(applicationContext, SentinelForegroundService::class.java).apply {
                        this.action = SentinelForegroundService.ACTION_WAKE
                        putExtra("EXTRA_FROM_FCM_WAKE", true)
                        putExtra("EXTRA_FCM_ACTION", action)
                        putExtra("EXTRA_FCM_TIMESTAMP", timestamp)
                    }

                    ContextCompat.startForegroundService(applicationContext, serviceIntent)
                }
                else -> {
                    Log.w(TAG, "FCM: Unhandled push action received: $action")
                    try {
                        if (wakeLock?.isHeld == true) wakeLock.release()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FCM: Error processing push wakeup: ${e.message}", e)
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Exception) {}
        }
    }
}
