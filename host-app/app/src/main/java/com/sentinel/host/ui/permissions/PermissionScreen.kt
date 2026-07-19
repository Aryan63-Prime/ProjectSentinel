package com.sentinel.host.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * Permission request screen shown before login.
 * Requests: Location, Microphone, Notifications, and Battery Optimization exemption.
 *
 * Background location is requested AFTER foreground location is granted
 * (Android requires this two-step flow).
 */
@Composable
fun PermissionScreen(
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var locationGranted by remember { mutableStateOf(false) }
    var backgroundLocationGranted by remember { mutableStateOf(false) }
    var microphoneGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    var batteryOptimized by remember { mutableStateOf(false) }

    // Recheck on resume (user may return from Settings)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            locationGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else true
            microphoneGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
            notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else true
            batteryOptimized = isBatteryOptimizationIgnored(context)
        }
    }

    val allGranted = locationGranted && backgroundLocationGranted &&
            microphoneGranted && notificationGranted && batteryOptimized

    // Auto-proceed when all granted
    LaunchedEffect(allGranted) {
        if (allGranted) onAllGranted()
    }

    // Permission launchers
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundLocationGranted = granted
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Sentinel Host",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. Location
        PermissionCard(
            title = "Location Access",
            description = "Required to stream GPS coordinates to the server.",
            granted = locationGranted,
            onRequest = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Background Location (only after foreground is granted)
        AnimatedVisibility(visible = locationGranted) {
            Column {
                PermissionCard(
                    title = "Background Location",
                    description = "Allows location tracking when the app is in the background. Tap 'Allow all the time' in Settings.",
                    granted = backgroundLocationGranted,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 3. Microphone
        PermissionCard(
            title = "Microphone",
            description = "Required for audio monitoring when requested by the admin.",
            granted = microphoneGranted,
            onRequest = {
                microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Notifications (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = "Notifications",
                description = "Required for the foreground service notification.",
                granted = notificationGranted,
                onRequest = {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 5. Battery Optimization
        PermissionCard(
            title = "Battery Optimization",
            description = "Disable battery optimization so the app stays connected in the background.",
            granted = batteryOptimized,
            onRequest = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (allGranted) {
            Button(
                onClick = onAllGranted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        } else {
            Text(
                text = "Grant all permissions to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (granted) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedButton(onClick = onRequest) {
                    Text("Grant")
                }
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
