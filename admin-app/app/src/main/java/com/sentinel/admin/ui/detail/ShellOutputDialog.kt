package com.sentinel.admin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ShellOutputDialog(
    lastOutput: String,
    onExecute: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var commandText by remember { mutableStateOf("uptime") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Host Diagnostic Shell Console",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        label = { Text("Shell Command") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onExecute(commandText) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Run")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tactical Quick Command Presets (B5 of 5)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "uptime" to "Uptime",
                        "top -n 1" to "top (CPU)",
                        "df -h" to "df (Disk)",
                        "ip route" to "Routes",
                        "dumpsys battery" to "Battery",
                        "pm list packages -3" to "App List"
                    )
                    presets.forEach { (cmd, label) ->
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                commandText = cmd
                                onExecute(cmd)
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFF19232E),
                                labelColor = Color(0xFFAAC7E8)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF293542)),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = lastOutput.ifEmpty { "Enter command above and tap Run." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF00FF66)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
