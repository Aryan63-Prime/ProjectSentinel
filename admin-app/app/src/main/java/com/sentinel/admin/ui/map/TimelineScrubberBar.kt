package com.sentinel.admin.ui.map

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
