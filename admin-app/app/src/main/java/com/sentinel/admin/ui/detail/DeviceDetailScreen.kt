package com.sentinel.admin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.sentinel.admin.domain.model.AudioStatistics
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceContactBook
import com.sentinel.admin.domain.model.DeviceLocation
import com.sentinel.admin.domain.model.PlaybackState

/**
 * Device Detail screen — displays all available information for a single device.
 *
 * Stateless composable — all state from [DeviceDetailUiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    uiState: DeviceDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onListenClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onRecordToggle: () -> Unit = {},
    onRecordingsClick: () -> Unit = {},
    onFilesClick: (String) -> Unit = {},
    onSystemInfoClick: () -> Unit = {},
    onTriggerBeaconClick: () -> Unit = {},
    onCapturePhotoClick: (useFront: Boolean) -> Unit = {},
    onFetchLogsClick: () -> Unit = {},
    onFetchNotifLogsClick: () -> Unit = {},
    onExecuteShellClick: (String) -> Unit = {},
    onCaptureScreenshotClick: () -> Unit = {},
    onPttStart: () -> Unit = {},
    onPttStop: () -> Unit = {},
    onSyncContactClick: () -> Unit = {},
    onOpenAddressBookClick: () -> Unit = {},
    onLockDeviceClick: () -> Unit = {},
    onToggleAntiTamper: (Boolean) -> Unit = {},
    onEnforcePermissions: () -> Unit = {},
    onRefreshMdmStatus: () -> Unit = {},
    onDismissMdmMessage: () -> Unit = {},
    onDismissDialogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = Color(0xFF080C14),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.device?.deviceName ?: "Device Details",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9),
                            fontSize = 17.sp
                        )
                        uiState.device?.let { dev ->
                            Text(
                                text = "${dev.model} · ${dev.deviceId}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF1F5F9)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF080C14),
                    titleContentColor = Color(0xFFF1F5F9)
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.device == null -> {
                    LoadingState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
                uiState.errorMessage != null && uiState.device == null -> {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = onRetry,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
                uiState.device != null -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        DeviceContent(
                            device = uiState.device,
                            isOnline = uiState.isOnline,
                            playbackState = uiState.playbackState,
                            audioStats = uiState.audioStats,
                            isRecording = uiState.isRecording,
                            recordingDurationMs = uiState.recordingDurationMs,
                            contactBook = uiState.contactBook,
                            isSyncingContacts = uiState.isSyncingContacts,
                            onSyncContactClick = onSyncContactClick,
                            onOpenAddressBookClick = onOpenAddressBookClick,
                            onListenClick = onListenClick,
                            onStopClick = onStopClick,
                            onRecordToggle = onRecordToggle,
                            onRecordingsClick = onRecordingsClick,
                            onFilesClick = { onFilesClick(uiState.device.deviceId) },
                            onSystemInfoClick = onSystemInfoClick,
                            onTriggerBeaconClick = onTriggerBeaconClick,
                            onCapturePhotoClick = onCapturePhotoClick,
                            onFetchLogsClick = onFetchLogsClick,
                            onFetchNotifLogsClick = onFetchNotifLogsClick,
                            onExecuteShellClick = { onExecuteShellClick("uptime") },
                            onCaptureScreenshotClick = onCaptureScreenshotClick,
                            onPttStart = onPttStart,
                            onPttStop = onPttStop,
                            isDeviceAdminActive = uiState.isDeviceAdminActive,
                            isDeviceOwner = uiState.isDeviceOwner,
                            isAntiTamperEnabled = uiState.isAntiTamperEnabled,
                            isLockingDevice = uiState.isLockingDevice,
                            mdmActionMessage = uiState.mdmActionMessage,
                            onLockDeviceClick = onLockDeviceClick,
                            onToggleAntiTamper = onToggleAntiTamper,
                            onEnforcePermissions = onEnforcePermissions,
                            onRefreshMdmStatus = onRefreshMdmStatus,
                            onDismissMdmMessage = onDismissMdmMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Render Air Command Dialogs
            if (uiState.showDiagnosticsDialog) {
                SystemDiagnosticsDialog(data = uiState.diagnosticsData, onDismiss = onDismissDialogs)
            }
            if (uiState.showPhotoDialog) {
                PhotoViewerDialog(
                    imageBase64 = uiState.capturedPhotoBase64,
                    cameraFacing = uiState.capturedPhotoFacing,
                    onDismiss = onDismissDialogs,
                    onSwitchCamera = { useFront -> onCapturePhotoClick(useFront) }
                )
            }
            if (uiState.showShellDialog) {
                ShellOutputDialog(lastOutput = uiState.shellOutput, onExecute = onExecuteShellClick, onDismiss = onDismissDialogs)
            }
            if (uiState.showLogsDialog) {
                LogsViewerDialog(logs = uiState.logsList, onDismiss = onDismissDialogs)
            }
            if (uiState.showNotifLogsDialog) {
                NotificationLogDialog(logsJsonRaw = uiState.notifLogsJsonRaw, onDismiss = onDismissDialogs)
            }
            if (uiState.showAddressBookDialog && uiState.contactBook != null) {
                AddressBookDialog(contactBook = uiState.contactBook, onDismiss = onDismissDialogs)
            }
            if (uiState.showScreenshotDialog) {
                ScreenshotViewerDialog(payload = uiState.screenshotPayload, onDismiss = onDismissDialogs)
            }
            if (uiState.showPreviewDialog) {
                FilePreviewDialog(payload = uiState.previewPayload, onDismiss = onDismissDialogs)
            }
        }
    }
}

// ============================================================
// Device Content
// ============================================================

@Composable
private fun DeviceContent(
    device: Device,
    isOnline: Boolean,
    playbackState: PlaybackState,
    audioStats: AudioStatistics,
    isRecording: Boolean,
    recordingDurationMs: Long,
    contactBook: DeviceContactBook? = null,
    isSyncingContacts: Boolean = false,
    onSyncContactClick: () -> Unit = {},
    onOpenAddressBookClick: () -> Unit = {},
    onListenClick: () -> Unit,
    onStopClick: () -> Unit,
    onRecordToggle: () -> Unit,
    onRecordingsClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSystemInfoClick: () -> Unit = {},
    onTriggerBeaconClick: () -> Unit = {},
    onCapturePhotoClick: (useFront: Boolean) -> Unit = {},
    onFetchLogsClick: () -> Unit = {},
    onFetchNotifLogsClick: () -> Unit = {},
    onExecuteShellClick: () -> Unit = {},
    onCaptureScreenshotClick: () -> Unit = {},
    onPttStart: () -> Unit = {},
    onPttStop: () -> Unit = {},
    isDeviceAdminActive: Boolean = false,
    isDeviceOwner: Boolean = false,
    isAntiTamperEnabled: Boolean = false,
    isLockingDevice: Boolean = false,
    mdmActionMessage: String? = null,
    onLockDeviceClick: () -> Unit = {},
    onToggleAntiTamper: (Boolean) -> Unit = {},
    onEnforcePermissions: () -> Unit = {},
    onRefreshMdmStatus: () -> Unit = {},
    onDismissMdmMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status header
        StatusHeader(device = device, isOnline = isOnline)

        // Device info card
        InfoCard(title = "Device Information") {
            InfoRow("Device Name", device.deviceName)
            InfoRow("Device ID", device.deviceId)
            InfoRow("Model", device.model)
            InfoRow("App Version", device.appVersion)
            InfoRow("Connection ID", device.connectionId)
        }

        // Connection card
        InfoCard(title = "Connection") {
            InfoRow("Status", if (isOnline) "Online" else "Offline")
            InfoRow("Authenticated", if (device.authenticated) "Yes" else "No")
            InfoRow("Registration", device.registrationState.replaceFirstChar { it.uppercase() })
            InfoRow("Connected At", formatTimestamp(device.connectedAt))
            InfoRow("Last Heartbeat", formatTimestamp(device.lastHeartbeat))
        }

        // Location card (if available)
        device.latestLocation?.let { location ->
            LocationCard(location = location)
        }

        // Map card
        DeviceLocationMap(
            location = device.latestLocation,
            deviceName = device.deviceName,
            isOnline = isOnline
        )

        // Remote Air Commands Engine
        AirCommandCard(
            isOnline = isOnline,
            onSystemInfoClick = onSystemInfoClick,
            onTriggerBeaconClick = onTriggerBeaconClick,
            onCapturePhotoClick = onCapturePhotoClick,
            onFetchLogsClick = onFetchLogsClick,
            onFetchNotifLogsClick = onFetchNotifLogsClick,
            onExecuteShellClick = onExecuteShellClick,
            onCaptureScreenshotClick = onCaptureScreenshotClick,
            onLockDeviceClick = onLockDeviceClick
        )

        // Enterprise MDM & Policy Card
        MdmPolicyCard(
            isOnline = isOnline,
            isDeviceAdminActive = isDeviceAdminActive,
            isDeviceOwner = isDeviceOwner,
            isAntiTamperEnabled = isAntiTamperEnabled,
            isLockingDevice = isLockingDevice,
            actionMessage = mdmActionMessage,
            onLockDevice = onLockDeviceClick,
            onToggleAntiTamper = onToggleAntiTamper,
            onEnforcePermissions = onEnforcePermissions,
            onRefreshStatus = onRefreshMdmStatus,
            onDismissMessage = onDismissMdmMessage
        )

        // Audio monitoring controls
        AudioControlCard(
            playbackState = playbackState,
            audioStats = audioStats,
            isRecording = isRecording,
            recordingDurationMs = recordingDurationMs,
            isOnline = isOnline,
            onListenClick = onListenClick,
            onStopClick = onStopClick,
            onRecordToggle = onRecordToggle,
            onRecordingsClick = onRecordingsClick,
            onPttStart = onPttStart,
            onPttStop = onPttStop
        )

        // File system controls
        FileControlCard(
            onFilesClick = onFilesClick
        )

        // Contact & Address Book Card (Real Host Contacts)
        ContactDetailsCard(
            contactBook = contactBook,
            isSyncing = isSyncingContacts,
            onSyncClick = onSyncContactClick,
            onOpenAddressBookClick = onOpenAddressBookClick
        )

        // Network card (from location data)
        device.latestLocation?.let { location ->
            val isCharging = location.network.contains("(Charging)")
            val cleanNetwork = location.network.replace(" (Charging)", "")
            InfoCard(title = "Network & Battery") {
                InfoRow("Network Type", cleanNetwork)
                InfoRow("Battery", "${location.battery}%" + if (isCharging) " (Charging)" else "")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ============================================================
// Status Header
// ============================================================

@Composable
private fun StatusHeader(
    device: Device,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(1.dp, if (isOnline) Color(0x3300E5FF) else Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0x1F00E5FF) else Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DevicesOther,
                    contentDescription = null,
                    tint = if (isOnline) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1F5F9)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFF64748B))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) Color(0xFF10B981) else Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = device.model,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            // Battery icon
            device.latestLocation?.let { loc ->
                val isCharging = loc.network.contains("(Charging)")
                val battColor = when {
                    loc.battery > 50 -> Color(0xFF10B981)
                    loc.battery > 20 -> Color(0xFFF59E0B)
                    else -> Color(0xFFF43F5E)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isCharging) {
                            Icons.Default.BatteryChargingFull
                        } else if (loc.battery > 50) {
                            Icons.Default.BatteryFull
                        } else {
                            Icons.Default.Battery4Bar
                        },
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = battColor
                    )
                    Text(
                        text = "${loc.battery}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = battColor
                    )
                }
            }
        }
    }
}

// ============================================================
// Location Card
// ============================================================

@Composable
private fun LocationCard(
    location: DeviceLocation,
    modifier: Modifier = Modifier
) {
    InfoCard(title = "Location Telemetry", icon = Icons.Default.LocationOn, modifier = modifier) {
        InfoRow("Latitude", "%.6f".format(location.latitude), isMonospace = true)
        InfoRow("Longitude", "%.6f".format(location.longitude), isMonospace = true)
        InfoRow("Accuracy", "±%.1f m".format(location.accuracy))
        InfoRow("Recorded At", formatTimestamp(location.recordedAt))
    }
}

// ============================================================
// Reusable Components
// ============================================================

@Composable
private fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFF38BDF8)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isMonospace: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = Color(0xFFF1F5F9)
        )
    }
}

// ============================================================
// State screens
// ============================================================

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading device…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

// ============================================================
// Utility
// ============================================================

/**
 * Formats an ISO-8601 timestamp for friendly display.
 * "2026-07-09T12:00:20Z" → "Jul 9, 2026 12:00"
 */
private fun formatTimestamp(iso: String): String {
    return try {
        val date = iso.substringBefore("T")
        val time = iso.substringAfter("T").substringBefore("Z").substring(0, 5)
        val parts = date.split("-")
        val months = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val month = months[parts[1].toInt() - 1]
        val day = parts[2].toInt()
        val year = parts[0]
        "$month $day, $year $time"
    } catch (_: Exception) {
        iso
    }
}
