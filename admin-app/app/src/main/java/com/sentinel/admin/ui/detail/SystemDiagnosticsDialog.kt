package com.sentinel.admin.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SystemDiagnosticsDialog(
    data: Map<String, Any>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Host Live System Diagnostics",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                val cpuUsage = data["cpuUsage"]?.toString() ?: "15%"
                val ramAvailable = data["ramAvailableMb"]?.toString() ?: "0"
                val ramTotal = data["ramTotalMb"]?.toString() ?: "100"
                val storageAvail = data["storageAvailableGb"]?.toString() ?: "N/A"
                val batteryPct = data["batteryPercent"]?.toString() ?: "0"
                val batteryTemp = data["batteryTempC"]?.toString() ?: "0.0"
                val wifiSsid = data["wifiSsid"]?.toString() ?: "Unknown"
                val isCharging = data["isCharging"] == true

                DiagnosticItem(label = "CPU Load", value = cpuUsage)
                DiagnosticItem(label = "RAM Available", value = "$ramAvailable MB / $ramTotal MB")
                DiagnosticItem(label = "Internal Storage Available", value = storageAvail)
                DiagnosticItem(label = "Battery", value = "$batteryPct% (${if (isCharging) "Charging" else "Discharging"}) @ $batteryTemp°C")
                DiagnosticItem(label = "Wi-Fi SSID", value = wifiSsid)

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "System Status: Nominal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
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

@Composable
private fun DiagnosticItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
