# Feature Specification & Code: Fleet Map Clustering & Historical Timeline Playback

## 1. Overview & Problem Statement
Currently, the Admin application displays only the latest instantaneous GPS fix for a single selected host. Fleet operators and dispatchers require two fundamental capabilities:
1. **Multi-Device Fleet Overview:** Viewing all registered host units on a single map with automatic marker clustering.
2. **Historical Timeline Scrubber & Speed Analytics:** An interactive 24-hour timeline scrubber that animates historical movement, shows speed-colored route segments (green/yellow/red), and highlights stationary dwell spots.

---

## 2. Domain Models (`admin-app/domain/`)

### File: `admin-app/domain/src/main/java/com/sentinel/admin/domain/model/HistoricalRoute.kt`

```kotlin
package com.sentinel.admin.domain.model

data class BreadcrumbPoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val timestamp: Long,
    val accuracy: Float
)

data class DwellCluster(
    val latitude: Double,
    val longitude: Double,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Long
)
```

---

## 3. Admin-App Implementation

### File: `admin-app/app/src/main/java/com/sentinel/admin/ui/map/TimelineScrubberBar.kt`

```kotlin
package com.sentinel.admin.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TimelineScrubberBar(
    startTime: Long,
    endTime: Long,
    currentTime: Long,
    onTimeChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }

    // Auto-playback loop
    LaunchedEffect(isPlaying, currentTime) {
        if (isPlaying) {
            delay(500)
            val nextTime = currentTime + 300 // Advance 5 minutes every tick
            if (nextTime >= endTime) {
                onTimeChanged(startTime)
                isPlaying = false
            } else {
                onTimeChanged(nextTime)
            }
        }
    }

    val totalDuration = (endTime - startTime).coerceAtLeast(1L).toFloat()
    val progress = ((currentTime - startTime).toFloat() / totalDuration).coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Text(
                    text = "Historical: ${formatter.format(Date(currentTime * 1000))}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    isPlaying = false
                    val newTime = (startTime + (newProgress * totalDuration)).toLong()
                    onTimeChanged(newTime)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
```

### File: `admin-app/app/src/main/java/com/sentinel/admin/ui/map/HistoricalRouteMap.kt`

```kotlin
package com.sentinel.admin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.sentinel.admin.domain.model.BreadcrumbPoint
import com.sentinel.admin.domain.model.DwellCluster

@Composable
fun HistoricalRouteMap(
    points: List<BreadcrumbPoint>,
    dwellSpots: List<DwellCluster>,
    selectedTimestamp: Long,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val initialPosition = remember(points) {
        LatLng(points.first().latitude, points.first().longitude)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition, 14f)
    }

    // Find the historical point matching the current scrubber timestamp
    val activePoint = remember(points, selectedTimestamp) {
        points.minByOrNull { kotlin.math.abs(it.timestamp - selectedTimestamp) } ?: points.last()
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false)
    ) {
        // Render speed-colored polyline segments
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            val segmentColor = when {
                p1.speedKmh > 80f -> Color.Red         // Speed violation
                p1.speedKmh > 40f -> Color(0xFFFFA000) // Moderate speed (Amber)
                else -> Color(0xFF2E7D32)              // Normal speed (Green)
            }

            Polyline(
                points = listOf(
                    LatLng(p1.latitude, p1.longitude),
                    LatLng(p2.latitude, p2.longitude)
                ),
                color = segmentColor,
                width = 10f
            )
        }

        // Render Dwell Point markers (locations where device stopped)
        dwellSpots.forEach { dwell ->
            Marker(
                state = MarkerState(position = LatLng(dwell.latitude, dwell.longitude)),
                title = "Stopped for ${dwell.durationMinutes} min",
                snippet = "From ${dwell.startTime} to ${dwell.endTime}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
        }

        // Active Historical Avatar Marker at current scrubber position
        Marker(
            state = MarkerState(position = LatLng(activePoint.latitude, activePoint.longitude)),
            title = "Speed: ${activePoint.speedKmh.toInt()} km/h",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
        )

        Circle(
            center = LatLng(activePoint.latitude, activePoint.longitude),
            radius = activePoint.accuracy.toDouble().coerceAtLeast(10.0),
            fillColor = Color(0x337B1FA2),
            strokeColor = Color(0xAA7B1FA2),
            strokeWidth = 2f
        )
    }
}
```
