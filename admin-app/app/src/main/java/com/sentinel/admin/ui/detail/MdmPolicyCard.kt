package com.sentinel.admin.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MdmPolicyCard(
    isOnline: Boolean,
    isDeviceAdminActive: Boolean,
    isDeviceOwner: Boolean,
    isAntiTamperEnabled: Boolean,
    isLockingDevice: Boolean,
    actionMessage: String?,
    onLockDevice: () -> Unit,
    onToggleAntiTamper: (Boolean) -> Unit,
    onEnforcePermissions: () -> Unit,
    onRefreshStatus: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151B22)
        ),
        border = BorderStroke(1.dp, Color(0xFF293542)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFAAC7E8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFFAAC7E8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ENTERPRISE MDM POLICY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFF8FB2D8)
                    )
                }

                IconButton(
                    onClick = onRefreshStatus,
                    enabled = isOnline,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh MDM",
                        tint = Color(0xFF9AA7B6),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badges: Admin & Owner Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Device Admin Badge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F14))
                        .border(BorderStroke(1.dp, Color(0xFF293542)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isDeviceAdminActive) Color(0xFF10B981) else Color(0xFF7D8997))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDeviceAdminActive) "ADMIN ACTIVE" else "ADMIN OFF",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeviceAdminActive) Color(0xFF10B981) else Color(0xFF7D8997),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Device Owner Badge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F14))
                        .border(BorderStroke(1.dp, Color(0xFF293542)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isDeviceOwner) Color(0xFFAAC7E8) else Color(0xFF7D8997))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDeviceOwner) "DEVICE OWNER" else "PROFILE USER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeviceOwner) Color(0xFFAAC7E8) else Color(0xFF7D8997),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row 1: Remote Lock & Enforce Permissions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLockDevice,
                    enabled = isOnline && !isLockingDevice,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF43F5E),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isLockingDevice) "Locking..." else "Lock Screen",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onEnforcePermissions,
                    enabled = isOnline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF0F2F4)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF293542))
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row 2: Anti-Tamper Uninstall Protection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B0F14))
                    .border(BorderStroke(1.dp, Color(0xFF293542)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isAntiTamperEnabled) Color(0xFFAAC7E8) else Color(0xFF7D8997),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Anti-Tamper Protection",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF0F2F4)
                            )
                            Text(
                                text = "Blocks unauthorized package uninstallation",
                                fontSize = 10.sp,
                                color = Color(0xFF7D8997)
                            )
                        }
                    }

                    Switch(
                        checked = isAntiTamperEnabled,
                        onCheckedChange = { onToggleAntiTamper(it) },
                        enabled = isOnline,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFAAC7E8),
                            checkedTrackColor = Color(0xFF283847),
                            uncheckedThumbColor = Color(0xFF7D8997),
                            uncheckedTrackColor = Color(0xFF293542)
                        )
                    )
                }
            }

            // Message Banner
            if (!actionMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF283847))
                        .border(BorderStroke(1.dp, Color(0xFFAAC7E8).copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = actionMessage,
                            fontSize = 11.sp,
                            color = Color(0xFFE0F7FA),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissMessage,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFAAC7E8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
