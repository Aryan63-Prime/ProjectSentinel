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
        const val ACTION_START = "com.sentinel.host.action.START"
        const val ACTION_WAKE = "com.sentinel.host.action.WAKE"

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
    @Inject lateinit var systemInfoProvider: com.sentinel.host.data.device.SystemInfoProvider
    @Inject lateinit var deviceRepository: com.sentinel.host.domain.repository.DeviceRepository
    @Inject lateinit var sessionManager: com.sentinel.host.domain.session.SessionManager

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled exception in Sentinel service scope: ${throwable.message}", throwable)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
    private var isMicrophoneElevated = false

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

        // Query and cache Firebase Cloud Messaging token for Push-to-Wake
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.i(TAG, "Proactively retrieved FCM registration token: $token")
                        sessionManager.saveFcmToken(token)
                    } else {
                        Log.w(TAG, "Failed to retrieve FCM token: ${task.exception?.message}")
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying FirebaseMessaging token: ${e.message}")
        }

        // Fall detector listener setup (dormant by default to prevent false sirens)
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
        // Activate fall monitoring (silent covert alarm by default)
        fallDetector.enable(soundAlarm = false)

        // Schedule exact rolling watchdog alarm
        com.sentinel.host.receiver.SentinelWatchdogReceiver.scheduleNext(this)

        // Wire on-demand session-scoped audio controls
        audioStreamer.onSessionTimeout = {
            Log.i(TAG, "Audio session timeout — auto dropping mic elevation")
            stopOnDemandAudio()
        }
        connectionSupervisor.onStartAudioRequested = { _, _ ->
            startOnDemandAudio()
        }
        connectionSupervisor.onStopAudioRequested = { _, _ ->
            stopOnDemandAudio()
        }
        commandProcessor.onStartAudioRequested = {
            startOnDemandAudio()
        }
        commandProcessor.onStopAudioRequested = {
            stopOnDemandAudio()
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
        if (intent?.action == "com.sentinel.host.SIMULATE_FALL") {
            val impact = intent.getFloatExtra("impactG", 4.8f)
            Log.w(TAG, "Triggering SIMULATE_FALL with impact $impact g")
            fallDetector.simulateFall(impact)
            return START_STICKY
        }

        val isFromBoot = intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) == true
        val isFcmWake = intent?.getBooleanExtra("EXTRA_FROM_FCM_WAKE", false) == true ||
                intent?.action == ACTION_WAKE
        Log.i(TAG, "SentinelForegroundService starting (flags=$flags, startId=$startId, isFromBoot=$isFromBoot, isFcmWake=$isFcmWake)")

        if (isFcmWake) {
            Log.i(TAG, "FCM wakeup detected — acquiring WakeLock for revival and forcing instant reconnect")
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = pm?.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "Sentinel:FcmWakeRevival"
            )
            wakeLock?.acquire(15_000L)
            connectionSupervisor.forceReconnect()
        }

        val notification = buildNotification("Scanning ...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var started = false
            // Baseline Idle mode: LOCATION | DATA_SYNC (Microphone unallocated, privacy dot OFF)
            try {
                var idleType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    idleType = idleType or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, idleType)
                Log.i(TAG, "startForeground succeeded with LOCATION|DATA_SYNC (idle baseline)")
                started = true
            } catch (e: Throwable) {
                Log.w(TAG, "LOCATION|DATA_SYNC startForeground failed: ${e.message}")
            }

            // Fallback: LOCATION only
            if (!started) {
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    Log.i(TAG, "startForeground succeeded with LOCATION only")
                    started = true
                } catch (e: Throwable) {
                    Log.w(TAG, "LOCATION startForeground failed: ${e.message}")
                }
            }

            // Fallback: DATA_SYNC only (Android 14+)
            if (!started && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    Log.i(TAG, "startForeground succeeded with DATA_SYNC fallback")
                    started = true
                } catch (e: Throwable) {
                    Log.w(TAG, "DATA_SYNC startForeground failed: ${e.message}")
                }
            }

            // Safe catch-all fallback
            if (!started) {
                try {
                    @Suppress("DEPRECATION")
                    startForeground(NOTIFICATION_ID, notification)
                    Log.i(TAG, "startForeground succeeded with default fallback")
                } catch (e: Throwable) {
                    Log.e(TAG, "All startForeground attempts failed safely: ${e.message}", e)
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: Throwable) {
                Log.e(TAG, "Legacy startForeground failed: ${e.message}", e)
            }
        }

        checkPermissionsAndSettings()

        val testCmd = intent?.getStringExtra("EXTRA_TEST_RAW_MSG_B64")?.let { b64 ->
            try {
                String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        } ?: intent?.getStringExtra("EXTRA_TEST_RAW_MSG")

        testCmd?.let { cmd ->
            Log.i(TAG, "Executing test command via intent: $cmd")
            commandProcessor.processCommand(cmd) { res ->
                Log.i(TAG, "Test command result: $res")
            }
        }

        // Start connection supervisor
        connectionSupervisor.start()
        registerNetworkCallback()

        startAutoRepairConnection()

        // Monitor state for notification updates + dynamic microphone elevation on boot
        serviceScope.launch {
            connectionSupervisor.state.collect { state ->
                val text = when (state) {
                    is ConnectionState.Ready -> {
                        sendTelemetryReportNow()
                        startPeriodicTelemetry()
                        if (isMicrophoneElevated) "Audio Surveillance Active" else "System Nominal"
                    }
                    is ConnectionState.Reconnecting -> {
                        periodicTelemetryJob?.cancel()
                        if (isMicrophoneElevated) stopOnDemandAudio()
                        "Syncing..."
                    }
                    is ConnectionState.Error -> {
                        periodicTelemetryJob?.cancel()
                        if (isMicrophoneElevated) stopOnDemandAudio()
                        "Syncing..."
                    }
                    is ConnectionState.Disconnected -> {
                        periodicTelemetryJob?.cancel()
                        if (isMicrophoneElevated) stopOnDemandAudio()
                        "Syncing..."
                    }
                    else -> "Syncing..."
                }
                updateNotification(text)
            }
        }

        return START_STICKY
    }

    private var periodicTelemetryJob: Job? = null

    private fun startPeriodicTelemetry() {
        periodicTelemetryJob?.cancel()
        periodicTelemetryJob = serviceScope.launch {
            while (isActive) {
                delay(20_000L)
                if (connectionSupervisor.state.value is ConnectionState.Ready) {
                    sendTelemetryReportNow()
                }
            }
        }
    }

    private fun sendTelemetryReportNow() {
        try {
            val devInfo = deviceRepository.getDeviceInfo()
            val sysInfo = systemInfoProvider.getSystemInfo()
            val lastLoc = locationStreamer.lastLocation
            val battery = sysInfo["batteryPercent"] as? Int ?: -1
            val isCharging = sysInfo["isCharging"] as? Boolean ?: false
            val wifiSsid = sysInfo["wifiSsid"] as? String ?: ""
            val network = if (isCharging) "WiFi (Charging)" else if (wifiSsid.isNotBlank()) "WiFi" else "Cellular"
            val lat = lastLoc?.latitude ?: 0.0
            val lng = lastLoc?.longitude ?: 0.0
            val acc = lastLoc?.accuracy?.toDouble() ?: 0.0

            val seq = System.currentTimeMillis()
            val ts = System.currentTimeMillis() / 1000
            val telemetryJson = """{"type":"COMMAND_RESULT","version":1,"timestamp":$ts,"sequence":$seq,"data":{"command":"TELEMETRY_REPORT","success":true,"payload":{"deviceId":"${devInfo.deviceId}","model":"${devInfo.model}","uniqueKey":"${devInfo.deviceId}_${devInfo.model}","latitude":$lat,"longitude":$lng,"accuracy":$acc,"battery":$battery,"network":"$network","timestamp":${System.currentTimeMillis()}}}}"""
            webSocketDataSource.sendText(telemetryJson)
            Log.d(TAG, "Sent isolated TELEMETRY_REPORT (model=${devInfo.model}, battery=$battery%, network=$network)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send TELEMETRY_REPORT: ${e.message}")
        }
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
     * Dynamically elevates the foreground service to include MICROPHONE type
     * only during an active on-demand listening session.
     */
    fun startOnDemandAudio() {
        if (isMicrophoneElevated && audioStreamer.isStreaming) {
            Log.d(TAG, "Audio already streaming on-demand")
            return
        }

        val hasMic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            Log.w(TAG, "Cannot start on-demand audio: RECORD_AUDIO permission not granted")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var activeType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activeType = activeType or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            try {
                val notification = buildNotification("Audio Surveillance Active")
                startForeground(NOTIFICATION_ID, notification, activeType)
                isMicrophoneElevated = true
                Log.i(TAG, "Dynamic FGS elevation to LOCATION|MICROPHONE succeeded — green privacy dot active")
            } catch (e: Throwable) {
                Log.w(TAG, "Dynamic FGS elevation to MICROPHONE failed: ${e.message}")
            }
        }

        audioStreamer.hasPermission = true
        audioStreamer.start(maxDurationSeconds = 300L)
        updateNotification("Audio Surveillance Active")
    }

    /**
     * Stops on-demand audio capture, completely releases the AudioRecord hardware instance,
     * and drops foreground service type back to LOCATION|DATA_SYNC so the Android green
     * microphone privacy dot turns OFF immediately.
     */
    fun stopOnDemandAudio() {
        Log.i(TAG, "Stopping on-demand audio capture and releasing microphone resources")
        audioStreamer.stop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var idleType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                idleType = idleType or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            try {
                val notification = buildNotification(if (connectionSupervisor.state.value is ConnectionState.Ready) "System Nominal" else "Syncing...")
                startForeground(NOTIFICATION_ID, notification, idleType)
                isMicrophoneElevated = false
                Log.i(TAG, "Dropped FGS type back to LOCATION|DATA_SYNC — mic released, privacy dot OFF")
            } catch (e: Throwable) {
                Log.w(TAG, "FGS drop back to idle type failed: ${e.message}")
            }
        }
        isMicrophoneElevated = false
        updateNotification(if (connectionSupervisor.state.value is ConnectionState.Ready) "System Nominal" else "Syncing...")
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
        stopOnDemandAudio()
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
