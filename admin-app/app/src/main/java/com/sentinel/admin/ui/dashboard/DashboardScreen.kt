package com.sentinel.admin.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.admin.domain.model.Device

/**
 * Dashboard screen displaying monitored devices.
 *
 * Stateless — all state from [DashboardUiState].
 * Events via lambda callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSortOrderChanged: (SortOrder) -> Unit,
    onFleetFilterChanged: (FleetFilter) -> Unit = {},
    onViewModeChanged: (ViewMode) -> Unit,
    onDeviceClick: (String) -> Unit,
    onRecordingsClick: () -> Unit = {},
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF080C14),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SENTINEL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = Color(0xFFF1F5F9)
                            )
                            Text(
                                text = "COMMAND CENTER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF080C14),
                    titleContentColor = Color(0xFFF1F5F9),
                    actionIconContentColor = Color(0xFF94A3B8)
                ),
                actions = {
                    // Recordings Gallery
                    IconButton(onClick = onRecordingsClick) {
                        Icon(Icons.Default.Mic, contentDescription = "Saved Recordings", tint = Color(0xFF38BDF8))
                    }
                    // Sort
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color(0xFF94A3B8))
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            order.label,
                                            fontWeight = if (order == uiState.sortOrder) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        )
                                    },
                                    onClick = {
                                        onSortOrderChanged(order)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    // Map/List toggle
                    IconButton(onClick = {
                        val newMode = if (uiState.viewMode == ViewMode.LIST) ViewMode.MAP else ViewMode.LIST
                        onViewModeChanged(newMode)
                    }) {
                        Icon(
                            if (uiState.viewMode == ViewMode.LIST) Icons.Default.Map else Icons.Default.ViewList,
                            contentDescription = if (uiState.viewMode == ViewMode.LIST) "Map view" else "List view",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                    // Refresh
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF94A3B8))
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChanged = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Fleet Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FleetFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        FleetFilter.ALL -> uiState.devices.size
                        FleetFilter.ONLINE -> uiState.devices.count { it.heartbeatStatus == "online" }
                        FleetFilter.LOW_BATTERY -> uiState.devices.count { (it.latestLocation?.battery ?: 100) <= 20 }
                        FleetFilter.EMERGENCY -> uiState.devices.count { (it.latestLocation?.battery ?: 100) <= 15 }
                    }
                    val isSelected = uiState.fleetFilter == filter
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { onFleetFilterChanged(filter) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF0F172A),
                            labelColor = Color(0xFF94A3B8),
                            selectedContainerColor = Color(0xFF083344),
                            selectedLabelColor = Color(0xFF00E5FF)
                        ),
                        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF1E293B),
                            selectedBorderColor = Color(0xFF00E5FF)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        label = {
                            Text(
                                text = "${filter.label} ($count)",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // Loading indicator
            AnimatedVisibility(visible = uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Content
            when {
                uiState.isLoading && uiState.devices.isEmpty() -> {
                    LoadingState()
                }
                uiState.errorMessage != null -> {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = onRetry
                    )
                }
                uiState.isEmpty -> {
                    EmptyState(
                        onRefresh = onRefresh,
                        onDemoDeviceClick = { onDeviceClick("HOST-0001") }
                    )
                }
                uiState.isSearchEmpty -> {
                    SearchEmptyState(query = uiState.searchQuery)
                }
                else -> {
                    if (uiState.viewMode == ViewMode.MAP) {
                        DashboardMapView(
                            markers = uiState.markers,
                            onMarkerClick = onDeviceClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = uiState.displayDevices,
                                    key = { it.connectionId }
                                ) { device ->
                                    DeviceCard(
                                        device = device,
                                        onClick = { onDeviceClick(device.deviceId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Search Bar
// ============================================================

@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text("Search units by name or ID…", color = Color(0xFF64748B), fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF0F172A),
            unfocusedContainerColor = Color(0xFF0F172A),
            focusedBorderColor = Color(0xFF00E5FF),
            unfocusedBorderColor = Color(0xFF1E293B),
            focusedTextColor = Color(0xFFF1F5F9),
            unfocusedTextColor = Color(0xFFF1F5F9)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    )
}

// ============================================================
// Device Card
// ============================================================

@Composable
private fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = device.heartbeatStatus == "online"
    val loc = device.latestLocation

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(1.dp, if (isOnline) Color(0x3300E5FF) else Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row: Icon + name + model + online badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0x1F00E5FF) else Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DevicesOther,
                            contentDescription = null,
                            tint = if (isOnline) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.model,
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = " · ",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = device.deviceId,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                // Status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isOnline) Color(0x1F10B981) else Color(0x1F64748B))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
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
                        color = if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry Inset Micro-Tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF080C14))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery
                val batt = loc?.battery ?: 0
                val battColor = when {
                    batt > 50 -> Color(0xFF10B981)
                    batt > 20 -> Color(0xFFF59E0B)
                    else -> Color(0xFFF43F5E)
                }
                val isCharging = loc?.network?.contains("(Charging)") == true
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "BATTERY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isCharging) Icons.Default.BatteryChargingFull else if (batt > 50) Icons.Default.BatteryFull else Icons.Default.Battery4Bar,
                            contentDescription = null,
                            tint = battColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (loc != null) "$batt%" else "--",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = battColor
                        )
                    }
                }

                // Uplink / Network
                val cleanNetwork = loc?.network?.replace(" (Charging)", "") ?: "Unknown"
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "UPLINK",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (cleanNetwork.contains("WiFi", ignoreCase = true)) Icons.Default.Wifi else Icons.Default.NetworkCell,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = cleanNetwork,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F5F9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Satellite / GPS
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "SATELLITE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (loc != null) "±${loc.accuracy.toInt()}m" else "No Fix",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (loc != null) Color(0xFF00E5FF) else Color(0xFF64748B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: registration state + last heartbeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "● ${device.registrationState.uppercase()}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "HEARTBEAT: ${formatTimestamp(device.lastHeartbeat)}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: @Composable () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        icon()
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================
// State screens
// ============================================================

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading devices…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(
    onRefresh: () -> Unit,
    onDemoDeviceClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.DevicesOther,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No devices found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect a Host device to begin monitoring",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh")
                }
                OutlinedButton(onClick = onDemoDeviceClick) {
                    Icon(Icons.Default.DevicesOther, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspect Device Details")
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(
    query: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No results for \"$query\"",
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
        modifier = modifier.fillMaxSize(),
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
 * Formats an ISO-8601 timestamp for display.
 * Shows time portion only for readability in cards.
 */
private fun formatTimestamp(iso: String): String {
    return try {
        // Extract HH:mm from "2026-07-09T12:00:20Z"
        val timePart = iso.substringAfter("T").substringBefore("Z")
        timePart.substring(0, 5)
    } catch (_: Exception) {
        iso
    }
}
