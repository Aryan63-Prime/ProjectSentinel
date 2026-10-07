package com.sentinel.admin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.sentinel.admin.domain.model.AudioStatistics
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceContactBook
import com.sentinel.admin.domain.model.DeviceLocation
import com.sentinel.admin.domain.model.PlaybackState
import com.sentinel.admin.ui.DeviceArtwork

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
    onTogglePttArm: (Boolean) -> Unit = {},
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
    var showLockConfirmation by remember { mutableStateOf(false) }
    var showBeaconConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF0B0F14),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.device?.let { device ->
                                device.deviceName.ifBlank { device.model.ifBlank { "Device ${device.displayId}" } }
                            } ?: "Device Details",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0F2F4),
                            fontSize = 17.sp
                        )
                        uiState.device?.let { dev ->
                            Text(
                                text = "${dev.model} · ${dev.displayId}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF8FB2D8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF0F2F4)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFFAAC7E8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0F14),
                    titleContentColor = Color(0xFFF0F2F4)
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
                            onTriggerBeaconClick = { showBeaconConfirmation = true },
                            onCapturePhotoClick = onCapturePhotoClick,
                            onFetchLogsClick = onFetchLogsClick,
                            onFetchNotifLogsClick = onFetchNotifLogsClick,
                            onExecuteShellClick = { onExecuteShellClick("uptime") },
                            onCaptureScreenshotClick = onCaptureScreenshotClick,
                            isPttArmed = uiState.isPttArmed,
                            isPttTransmitting = uiState.isPttTransmitting,
                            pttAudioLevel = uiState.pttAudioLevel,
                            onTogglePttArm = onTogglePttArm,
                            onPttStart = onPttStart,
                            onPttStop = onPttStop,
                            isDeviceAdminActive = uiState.isDeviceAdminActive,
                            isDeviceOwner = uiState.isDeviceOwner,
                            isAntiTamperEnabled = uiState.isAntiTamperEnabled,
                            isLockingDevice = uiState.isLockingDevice,
                            mdmActionMessage = uiState.mdmActionMessage,
                            onLockDeviceClick = { if (!uiState.isLockingDevice) showLockConfirmation = true },
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
            if (showLockConfirmation) {
                AlertDialog(
                    onDismissRequest = { showLockConfirmation = false },
                    title = { Text("Lock this device?") },
                    text = { Text("The device screen will lock immediately. Its user may need their screen lock to continue.") },
                    confirmButton = {
                        TextButton(
                            enabled = !uiState.isLockingDevice,
                            onClick = {
                                showLockConfirmation = false
                                onLockDeviceClick()
                            }
                        ) { Text(if (uiState.isLockingDevice) "Locking…" else "Lock screen") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLockConfirmation = false }) { Text("Cancel") }
                    },
                    containerColor = Color(0xFF171F28),
                    titleContentColor = Color(0xFFF0F2F4),
                    textContentColor = Color(0xFFBFC9D5)
                )
            }
            if (showBeaconConfirmation) {
                AlertDialog(
                    onDismissRequest = { showBeaconConfirmation = false },
                    title = { Text("Activate the siren beacon?") },
                    text = { Text("This will play a loud alert through the device speaker.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBeaconConfirmation = false
                                onTriggerBeaconClick()
                            }
                        ) { Text("Activate beacon") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBeaconConfirmation = false }) { Text("Cancel") }
                    },
                    containerColor = Color(0xFF171F28),
                    titleContentColor = Color(0xFFF0F2F4),
                    textContentColor = Color(0xFFBFC9D5)
                )
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
    isPttArmed: Boolean = false,
    isPttTransmitting: Boolean = false,
    pttAudioLevel: Float = 0f,
    onTogglePttArm: (Boolean) -> Unit = {},
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status header
        StatusHeader(device = device, isOnline = isOnline)

        DeviceTelemetryStrip(device = device, isOnline = isOnline)

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

        DeviceConnectionCard(device = device, isOnline = isOnline)

        device.latestLocation?.let { location ->
            LocationCard(location = location)
        }

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
            isPttArmed = isPttArmed,
            isPttTransmitting = isPttTransmitting,
            pttAudioLevel = pttAudioLevel,
            onTogglePttArm = onTogglePttArm,
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
    val displayName = device.deviceName.ifBlank { device.model.ifBlank { "Device ${device.displayId}" } }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121923)
        ),
        border = BorderStroke(1.dp, if (isOnline) Color(0xFF31558A) else Color(0xFF26354E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status avatar
            DeviceArtwork(
                width = 66.dp,
                height = 90.dp,
                model = "${device.model} ${device.deviceName}"
            )
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF0F2F4)
                )
                Spacer(modifier = Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFF7D8997))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) Color(0xFF10B981) else Color(0xFF7D8997),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = device.model.ifBlank { "DEVICE" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8FB2D8)
                    )
                }
                Spacer(modifier = Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    DeviceTag(text = device.displayId)
                    DeviceTag(text = if (device.authenticated) "SECURE LINK" else "UNVERIFIED")
                }
            }
        }
    }
}

@Composable
private fun DeviceTag(text: String) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = .3.sp,
        color = Color(0xFFB7C9DD),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF283847))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun DeviceTelemetryStrip(device: Device, isOnline: Boolean) {
    val location = device.latestLocation
    val network = location?.network?.replace(" (Charging)", "") ?: "No signal"
    val batteryColor = when {
        location == null -> Color(0xFF7184A4)
        location.battery > 50 -> Color(0xFF48E4B6)
        location.battery > 20 -> Color(0xFFFFCA72)
        else -> Color(0xFFFF7588)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        TelemetryTile(
            icon = if (location?.network?.contains("(Charging)") == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
            value = location?.let { "${it.battery}%" } ?: "--",
            label = "BATTERY",
            tint = batteryColor,
            modifier = Modifier.weight(1f)
        )
        TelemetryTile(
            icon = Icons.Default.NetworkCell,
            value = network,
            label = "UPLINK",
            tint = Color(0xFF8FB2D8),
            modifier = Modifier.weight(1f)
        )
        TelemetryTile(
            icon = Icons.Default.Wifi,
            value = if (isOnline) "Connected" else "Offline",
            label = "CONNECTION",
            tint = if (isOnline) Color(0xFF45E0B3) else Color(0xFF8494B0),
            modifier = Modifier.weight(1f)
        )
        TelemetryTile(
            icon = Icons.Default.LocationOn,
            value = location?.let { "±${it.accuracy.toInt()}m" } ?: "No fix",
            label = "LOCATION",
            tint = Color(0xFFAAC7E8),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171F28))
            .border(1.dp, Color(0xFF293542), RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE8EDF2), maxLines = 1)
        Text(label, fontSize = 7.sp, letterSpacing = .3.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8B97A5), maxLines = 1)
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
    InfoCard(title = "Location details", icon = Icons.Default.LocationOn, modifier = modifier) {
        InfoRow("Coordinates", "%.5f, %.5f".format(location.latitude, location.longitude), isMonospace = true)
        InfoRow("Accuracy", "±%.1f m".format(location.accuracy))
        InfoRow("Updated", formatTimestamp(location.recordedAt))
    }
}

@Composable
private fun DeviceConnectionCard(device: Device, isOnline: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171F28)),
        border = BorderStroke(1.dp, Color(0xFF293542)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DevicesOther, contentDescription = null, tint = Color(0xFFAAC7E8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DEVICE & CONNECTION",
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .8.sp,
                    color = Color(0xFFAAC7E8)
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Hide technical details" else "Show technical details",
                        tint = Color(0xFF9AA7B6)
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            InfoRow("Model", device.model.ifBlank { "Unknown" })
            InfoRow("App version", device.appVersion.ifBlank { "—" })
            InfoRow("Connection", if (isOnline && device.authenticated) "Online · authenticated" else if (isOnline) "Online" else "Offline")
            InfoRow("Last seen", formatTimestamp(device.lastHeartbeat))
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column {
                    InfoRow("Callsign", device.displayId)
                    InfoRow("Device Token", device.deviceId)
                    InfoRow("Registration", device.registrationState.replaceFirstChar { it.uppercase() })
                    InfoRow("Connected", formatTimestamp(device.connectedAt))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Connection ID", fontSize = 12.sp, color = Color(0xFF9AA7B6), fontWeight = FontWeight.Medium)
                        Text(
                            text = device.connectionId,
                            fontSize = 11.sp,
                            color = Color(0xFFF0F2F4),
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        )
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(device.connectionId)) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy connection ID",
                                tint = Color(0xFFAAC7E8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF171F28)
        ),
        border = BorderStroke(1.dp, Color(0xFF293542)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFFAAC7E8)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFFAAC7E8)
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
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
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF9AA7B6),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.85f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = Color(0xFFF0F2F4),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1.15f)
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
