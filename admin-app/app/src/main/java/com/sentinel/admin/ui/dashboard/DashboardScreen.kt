package com.sentinel.admin.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.ui.DeviceArtwork
import com.sentinel.admin.ui.FleetOrb

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
    val onlineCount = uiState.devices.count { it.heartbeatStatus == "online" }

    Scaffold(
        containerColor = Color(0xFF0B0F14),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFAAC7E8))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SENTINEL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = Color(0xFFF0F2F4)
                            )
                            Text(
                                text = "COMMAND CENTER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0xFFAAC7E8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0F14),
                    titleContentColor = Color(0xFFF0F2F4),
                    actionIconContentColor = Color(0xFF9AA7B6)
                ),
                actions = {
                    // Recordings Gallery
                    IconButton(onClick = onRecordingsClick) {
                        Icon(Icons.Default.Mic, contentDescription = "Saved Recordings", tint = Color(0xFF8FB2D8))
                    }
                    // Sort
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color(0xFF9AA7B6))
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
                            tint = Color(0xFFAAC7E8)
                        )
                    }
                    // Refresh
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF9AA7B6))
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
            FleetOverviewCard(
                total = uiState.devices.size,
                online = onlineCount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FleetFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        FleetFilter.ALL -> uiState.devices.size
                        FleetFilter.ONLINE -> uiState.devices.count { it.heartbeatStatus == "online" }
                        FleetFilter.LOW_BATTERY -> uiState.devices.count { (it.latestLocation?.battery ?: 100) <= 20 }
                        FleetFilter.EMERGENCY -> uiState.devices.count { (it.latestLocation?.battery ?: 100) <= 15 }
                    }
                    val isSelected = uiState.fleetFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF283847) else Color(0xFF151B22))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFAAC7E8).copy(alpha = 0.62f) else Color(0xFF293542),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onFleetFilterChanged(filter) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${filter.label} $count",
                            fontSize = 10.sp,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFFAAC7E8) else Color(0xFF9AA7B6)
                        )
                    }
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
                                    key = { it.uniqueKey }
                                ) { device ->
                                    DeviceCard(
                                        device = device,
                                        onClick = { onDeviceClick(device.uniqueKey) }
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
// Fleet Overview
// ============================================================

@Composable
private fun FleetOverviewCard(
    total: Int,
    online: Int,
    modifier: Modifier = Modifier
) {
    val offline = (total - online).coerceAtLeast(0)
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(178.dp)
            .shadow(10.dp, shape, ambientColor = Color(0xFF52708F).copy(alpha = .14f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF18212A), Color(0xFF11171E), Color(0xFF1C252E))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color(0xFF52677D).copy(alpha = .5f), Color(0xFF293542), Color(0xFF293542))),
                shape = shape
            )
    ) {
        FleetOrb(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(112.dp)
                .alpha(.14f)
                .offset(x = 12.dp, y = (-5).dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = "FLEET STATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                color = Color(0xFFB7C9DD)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$online / $total",
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF0F2F4)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (online > 0) Color(0xFF39E4B7) else Color(0xFF7487A8))
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (online == total && total > 0) "All devices online" else "$online devices reporting",
                    fontSize = 12.sp,
                    color = Color(0xFFABC0DF)
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C1218).copy(alpha = .72f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FleetMetric("TOTAL DEVICES", total.toString(), Color(0xFF75B8FF))
                FleetMetric("ONLINE", online.toString(), Color(0xFF41E1B6))
                FleetMetric("OFFLINE", offline.toString(), Color(0xFF899AB8))
            }
        }
    }
}

@Composable
private fun FleetMetric(label: String, value: String, tint: Color) {
    Column {
        Text(label, fontSize = 8.sp, letterSpacing = .7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF778BAA))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = tint)
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
        placeholder = { Text("Search units by name or ID…", color = Color(0xFF7D8997), fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFAAC7E8), modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color(0xFF9AA7B6), modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF151B22),
            unfocusedContainerColor = Color(0xFF151B22),
            focusedBorderColor = Color(0xFFAAC7E8),
            unfocusedBorderColor = Color(0xFF293542),
            focusedTextColor = Color(0xFFF0F2F4),
            unfocusedTextColor = Color(0xFFF0F2F4)
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.988f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "deviceCardPressScale"
    )
    val pressTilt by animateFloatAsState(
        targetValue = if (pressed) 0.3f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "deviceCardPressTilt"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                rotationY = pressTilt
                cameraDistance = 18 * density
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151B22)
        ),
        border = BorderStroke(1.dp, if (isOnline) Color(0x3300E5FF) else Color(0xFF293542)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 7.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
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
                    DeviceArtwork(
                        modifier = Modifier.padding(end = 12.dp),
                        width = 48.dp,
                        height = 66.dp,
                        model = "${device.model} ${device.deviceName}"
                    )
                    val displayName = device.deviceName.ifBlank { device.model.ifBlank { "Unit ${device.displayId}" } }
                    val displayModel = device.model.ifBlank { "Mobile Unit" }
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0F2F4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayModel,
                                fontSize = 11.sp,
                                color = Color(0xFF8FB2D8),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = " · ",
                                fontSize = 11.sp,
                                color = Color(0xFF7D8997)
                            )
                            Text(
                                text = device.displayId,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF7D8997)
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
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFF7D8997))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) Color(0xFF10B981) else Color(0xFF9AA7B6),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry Inset Micro-Tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B0F14))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
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
                        color = Color(0xFF7D8997)
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
                        color = Color(0xFF7D8997)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (cleanNetwork.contains("WiFi", ignoreCase = true)) Icons.Default.Wifi else Icons.Default.NetworkCell,
                            contentDescription = null,
                            tint = Color(0xFF8FB2D8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = cleanNetwork,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF0F2F4),
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
                        color = Color(0xFF7D8997)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFAAC7E8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (loc != null) "±${loc.accuracy.toInt()}m" else "No Fix",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (loc != null) Color(0xFFAAC7E8) else Color(0xFF7D8997)
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
                    color = Color(0xFF7D8997)
                )
                Text(
                    text = "HEARTBEAT: ${formatTimestamp(device.lastHeartbeat)}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF9AA7B6)
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
