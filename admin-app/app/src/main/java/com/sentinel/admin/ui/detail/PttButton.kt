package com.sentinel.admin.ui.detail

import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Tactical Push-To-Talk (PTT) Intercom Controller.
 *
 * Features:
 * - Safety lock toggle to prevent accidental mic broadcast
 * - Hold-to-speak interactive transmitter with haptic feedback
 * - Visual live waveform audio level meter
 * - Cybernetic glow animations and fail-safe gesture filtering
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PttButton(
    isArmed: Boolean,
    isTransmitting: Boolean,
    audioLevel: Float,
    isOnline: Boolean,
    onToggleArm: (Boolean) -> Unit,
    onPressStart: () -> Unit,
    onPressRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isFingerDown by remember { mutableStateOf(false) }

    // Pulsing animation for active transmission
    val infiniteTransition = rememberInfiniteTransition(label = "pttPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F151C))
            .border(1.dp, Color(0xFF202A36), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Safety Lock Control Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isArmed) Color(0xFFF59E0B).copy(alpha = 0.18f)
                            else Color(0xFF10B981).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isArmed) Icons.Default.Warning else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isArmed) Color(0xFFF59E0B) else Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = if (isArmed) "INTERCOM ARMED (READY)" else "INTERCOM SAFE (LOCKED)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (isArmed) Color(0xFFFBBF24) else Color(0xFF10B981)
                    )
                    Text(
                        text = if (isArmed) "Hold button to broadcast voice to room"
                               else "Voice lock enabled — no accidental speech",
                        fontSize = 10.sp,
                        color = Color(0xFF8A99A8)
                    )
                }
            }

            Switch(
                checked = isArmed,
                onCheckedChange = { targetState ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleArm(targetState)
                },
                enabled = isOnline,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFF59E0B),
                    checkedTrackColor = Color(0xFFF59E0B).copy(alpha = 0.35f),
                    uncheckedThumbColor = Color(0xFF7D8997),
                    uncheckedTrackColor = Color(0xFF1A232E)
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. Interactive Hold-to-Speak Transmitter ---
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center
        ) {
            // Expanding pulse ripple when transmitting
            if (isTransmitting) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFFF43F5E).copy(alpha = pulseAlpha))
                )
            }

            val buttonBg = when {
                !isOnline -> Color(0xFF1A232E)
                !isArmed -> Color(0xFF161E28)
                isTransmitting -> Color(0xFFE11D48)
                else -> Color(0xFF0284C7)
            }

            val buttonBorder = when {
                !isOnline -> Color(0xFF24303F)
                !isArmed -> Color(0xFF293542)
                isTransmitting -> Color(0xFFFDA4AF)
                else -> Color(0xFF38BDF8)
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(if (isFingerDown && isArmed) 1.08f else 1.0f)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .border(2.dp, buttonBorder, CircleShape)
                    .pointerInteropFilter { motionEvent ->
                        if (!isOnline || !isArmed) {
                            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            return@pointerInteropFilter false
                        }
                        when (motionEvent.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isFingerDown = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPressStart()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isFingerDown = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPressRelease()
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        !isArmed -> Icons.Default.Lock
                        isTransmitting -> Icons.Default.RecordVoiceOver
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Push to Talk",
                    tint = when {
                        !isArmed -> Color(0xFF6B7A8D)
                        isTransmitting -> Color.White
                        else -> Color.White
                    },
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. Dynamic Waveform & Status Indicator ---
        if (isTransmitting) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(20.dp)
            ) {
                // 5 visualizer wave bars based on real-time audioLevel
                val baseLevel = audioLevel.coerceIn(0.15f, 1f)
                val heights = listOf(
                    (8 * baseLevel).coerceAtLeast(4f),
                    (18 * baseLevel).coerceAtLeast(6f),
                    (24 * baseLevel).coerceAtLeast(8f),
                    (16 * baseLevel).coerceAtLeast(6f),
                    (10 * baseLevel).coerceAtLeast(4f)
                )
                heights.forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFF43F5E))
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "TRANSMITTING VOICE TO HOST LOUDSPEAKER...",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = Color(0xFFF43F5E)
            )
            Text(
                text = "Live 16 kHz Wideband Voice Uplink Active",
                fontSize = 10.sp,
                color = Color(0xFFFDA4AF)
            )
        } else if (!isArmed) {
            Text(
                text = "PTT LOCKED — ARM SAFETY SWITCH TO TRANSMIT",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6B7A8D)
            )
            Text(
                text = "Prevents unintended background audio playback on target device",
                fontSize = 10.sp,
                color = Color(0xFF4C5866)
            )
        } else {
            Text(
                text = "HOLD BUTTON TO SPEAK (INTERCOM)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8)
            )
            Text(
                text = "Release button when done speaking to cut microphone",
                fontSize = 10.sp,
                color = Color(0xFF8A99A8)
            )
        }
    }
}
