package com.sentinel.admin.ui.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.admin.domain.model.AudioStatistics
import com.sentinel.admin.domain.model.PlaybackState

/**
 * Audio monitoring controls for the Device Detail screen.
 *
 * Displays:
 * - Listen/Stop buttons
 * - Playback state indicator (connecting, buffering, playing, paused, error)
 * - Audio statistics (frames, latency, PLC)
 */
@Composable
fun AudioControlCard(
    playbackState: PlaybackState,
    audioStats: AudioStatistics,
    onListenClick: () -> Unit,
    onStopClick: () -> Unit,
    isRecording: Boolean = false,
    recordingDurationMs: Long = 0L,
    onRecordToggle: () -> Unit = {},
    onRecordingsClick: () -> Unit = {},
    isOnline: Boolean = true,
    onPttStart: () -> Unit = {},
    onPttStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUDIO SURVEILLANCE & PTT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFF38BDF8)
                    )
                }

                if (isRecording) {
                    val seconds = (recordingDurationMs / 1000) % 60
                    val minutes = (recordingDurationMs / (1000 * 60)) % 60
                    val timeStr = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF43F5E).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF43F5E))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REC $timeStr",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF43F5E)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status indicator
            PlaybackStatusIndicator(playbackState = playbackState)

            Spacer(modifier = Modifier.height(14.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isActive = playbackState is PlaybackState.Connecting ||
                    playbackState is PlaybackState.Buffering ||
                    playbackState is PlaybackState.Playing

                Button(
                    onClick = onListenClick,
                    enabled = !isActive && isOnline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF00363D)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Listen", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onRecordToggle,
                    enabled = (isActive || isRecording) && isOnline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFF43F5E) else Color(0xFF0284C7),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isRecording) "Stop REC" else "Record", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onStopClick,
                    enabled = isActive || playbackState is PlaybackState.Paused,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF1F5F9)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // View Saved Recordings shortcut button
            androidx.compose.material3.TextButton(
                onClick = onRecordingsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "View Saved Recordings",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Two-Way Push-To-Talk Intercom
            PttButton(
                isOnline = isOnline,
                onPressStart = onPttStart,
                onPressRelease = onPttStop
            )

            // Stats (only show when active or recently active)
            if (audioStats.framesReceived > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                AudioStatsSection(audioStats = audioStats)
            }
        }
    }
}

@Composable
private fun PlaybackStatusIndicator(playbackState: PlaybackState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (playbackState) {
            is PlaybackState.Idle, is PlaybackState.Stopped -> {
                StatusDot(color = Color(0xFF64748B))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Monitoring standby",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }
            is PlaybackState.Connecting -> {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF00E5FF))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Establishing uplink…",
                    fontSize = 12.sp,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.SemiBold
                )
            }
            is PlaybackState.Buffering -> {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFF59E0B))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Buffering audio frames…",
                    fontSize = 12.sp,
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.SemiBold
                )
            }
            is PlaybackState.Playing -> {
                PulsingDot()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "LIVE AUDIO STREAMING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    letterSpacing = 0.5.sp
                )
            }
            is PlaybackState.Paused -> {
                StatusDot(color = Color(0xFFF59E0B))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Paused (connection drop)",
                    fontSize = 12.sp,
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.Medium
                )
            }
            is PlaybackState.Error -> {
                StatusDot(color = Color(0xFFF43F5E))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Error: ${playbackState.message}",
                    fontSize = 12.sp,
                    color = Color(0xFFF43F5E),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(9.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Color(0xFF10B981))
    )
}

@Composable
private fun AudioStatsSection(audioStats: AudioStatistics) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF080C14))
            .padding(12.dp)
    ) {
        Text(
            text = "OPUS TELEMETRY STREAM",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem("Received", "${audioStats.framesReceived}")
            StatItem("Decoded", "${audioStats.framesDecoded}")
            StatItem("Dropped", "${audioStats.framesDropped}")
            StatItem("PLC", "${audioStats.plcFrames}")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem("Latency", "${audioStats.currentLatencyMs}ms")
            StatItem("Late", "${audioStats.latePackets}")
            StatItem("Dups", "${audioStats.duplicatePackets}")
            StatItem("Errors", "${audioStats.decoderFailures}")
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = Color(0xFFF1F5F9)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.Medium
        )
    }
}
