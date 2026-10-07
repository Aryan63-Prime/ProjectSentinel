package com.sentinel.admin.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FullScreenLogsDialog(
    logs: List<String>,
    isOnline: Boolean,
    isFetching: Boolean,
    initialFullDevice: Boolean = false,
    initialFilter: String = "",
    onFetchLogs: (fullDevice: Boolean, filter: String) -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    var isFullDevice by remember { mutableStateOf(initialFullDevice) }
    var searchQuery by remember { mutableStateOf(initialFilter) }
    val scrollState = rememberScrollState()
    val chipScrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    var copiedNotification by remember { mutableStateOf(false) }

    // Client-side instant filter on top of received logs
    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) {
            logs
        } else {
            val query = searchQuery.trim().lowercase()
            val aliases = when (query) {
                "facebook", "fb" -> listOf("facebook", "katana", "fb", "com.facebook")
                "yt", "youtube" -> listOf("youtube", "google.android.youtube", "yt")
                "host", "sentinel" -> listOf("sentinel", "host", "cmdproc")
                else -> listOf(query)
            }
            logs.filter { line ->
                aliases.any { alias -> line.contains(alias, ignoreCase = true) }
            }
        }
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    LaunchedEffect(copiedNotification) {
        if (copiedNotification) {
            kotlinx.coroutines.delay(2000L)
            copiedNotification = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D12)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 36.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isFullDevice) "SYSTEM-WIDE DEVICE LOGCAT" else "HOST APP RUNTIME LOGS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = Color(0xFF34D399)
                        )
                        Text(
                            text = "${filteredLogs.size} lines shown" +
                                    (if (searchQuery.isNotBlank()) " · Filter: \"$searchQuery\"" else "") +
                                    (if (isOnline) " · Device Online" else " · Offline"),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF8B97A5)
                        )
                    }

                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scope Selectors Row: Host Only vs Full Device
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SCOPE:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )

                    FilterChip(
                        selected = !isFullDevice,
                        onClick = {
                            isFullDevice = false
                            onFetchLogs(false, searchQuery)
                        },
                        label = { Text("Host App", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0F766E),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1A222C),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = !isFullDevice,
                            borderColor = Color(0xFF293542),
                            selectedBorderColor = Color(0xFF14B8A6)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    FilterChip(
                        selected = isFullDevice,
                        onClick = {
                            isFullDevice = true
                            onFetchLogs(true, searchQuery)
                        },
                        label = { Text("Full Device (Logcat)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E3A8A),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1A222C),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isFullDevice,
                            borderColor = Color(0xFF293542),
                            selectedBorderColor = Color(0xFF3B82F6)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Preset Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val presets = listOf(
                        "All" to "",
                        "Host" to "Host",
                        "YouTube" to "yt",
                        "Facebook" to "facebook",
                        "System" to "system",
                        "Audio" to "audio",
                        "Camera" to "camera"
                    )

                    presets.forEach { (label, term) ->
                        val isSelected = (term.isEmpty() && searchQuery.isEmpty()) ||
                                (term.isNotEmpty() && searchQuery.equals(term, ignoreCase = true))

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                searchQuery = term
                                onFetchLogs(isFullDevice, term)
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                                selectedLabelColor = Color(0xFF34D399),
                                containerColor = Color(0xFF131B24),
                                labelColor = Color(0xFF718096)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color(0xFF232D3B),
                                selectedBorderColor = Color(0xFF10B981)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Query Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF131A22))
                        .border(1.dp, Color(0xFF253342), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = Color(0xFFF1F5F9),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(Color(0xFF34D399)),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Filter query (e.g. facebook, yt, host, error)...",
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                onFetchLogs(isFullDevice, "")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tactical Terminal Log Output View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF040608))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    if (filteredLogs.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            filteredLogs.forEach { line ->
                                val textColor = when {
                                    line.contains("ERROR") || line.contains("FATAL") || line.contains(" E/") -> Color(0xFFF87171)
                                    line.contains("WARN") || line.contains(" W/") -> Color(0xFFFBBF24)
                                    line.contains("HOST:CORE") || line.contains("SERVICE") || line.contains("CmdProc") -> Color(0xFF38BDF8)
                                    line.contains("NET") || line.contains("WEBSOCKET") -> Color(0xFF34D399)
                                    line.contains("AUDIO") || line.contains("CAMERA") -> Color(0xFFA78BFA)
                                    line.contains("katana") || line.contains("facebook", ignoreCase = true) -> Color(0xFF60A5FA)
                                    line.contains("youtube", ignoreCase = true) || line.contains("yt", ignoreCase = true) -> Color(0xFFF87171)
                                    else -> Color(0xFF94A3B8)
                                }
                                Text(
                                    text = line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = textColor
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isOnline) {
                                        if (searchQuery.isNotBlank()) "No logs found matching \"$searchQuery\""
                                        else "Terminal idle. Tap 'Pull Fresh Trace' below."
                                    } else {
                                        "Host offline. Connect device to capture live logs."
                                    },
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                if (isOnline && searchQuery.isNotBlank()) {
                                    Text(
                                        text = "Tap 'Pull Fresh Trace' to query host for \"$searchQuery\"",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF34D399)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onFetchLogs(isFullDevice, searchQuery) },
                        enabled = isOnline && !isFetching,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1.3f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFullDevice) "Pull Device Logs" else "Pull Host Logs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val allText = filteredLogs.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(allText))
                            copiedNotification = true
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFAAC7E8))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (copiedNotification) "Copied!" else "Copy All",
                            fontSize = 11.sp,
                            color = if (copiedNotification) Color(0xFF34D399) else Color(0xFFAAC7E8)
                        )
                    }

                    OutlinedButton(
                        onClick = onClearLogs,
                        enabled = logs.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.8f).height(40.dp)
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}
