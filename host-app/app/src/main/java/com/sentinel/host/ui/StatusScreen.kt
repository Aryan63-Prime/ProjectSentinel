package com.sentinel.host.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sentinel.host.domain.model.ConnectionState

/**
 * Simple status screen shown while the app is auto-connecting.
 */
@Composable
fun StatusScreen(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sentinel Host",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        val statusText = when (state) {
            is ConnectionState.Disconnected -> "Starting..."
            is ConnectionState.Connecting -> "Connecting..."
            is ConnectionState.Connected -> "Connected"
            is ConnectionState.Authenticating -> "Authenticating..."
            is ConnectionState.Authenticated -> "Authenticated"
            is ConnectionState.Registering -> "Registering..."
            is ConnectionState.Reconnecting -> "Reconnecting (attempt ${state.attempt})..."
            is ConnectionState.Error -> "Error: ${state.message}"
            is ConnectionState.Ready -> "Ready"
        }

        CircularProgressIndicator()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
