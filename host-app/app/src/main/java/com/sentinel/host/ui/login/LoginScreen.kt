package com.sentinel.host.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sentinel.host.domain.model.ConnectionState

/**
 * Minimal login screen per user requirement:
 * - Server URL
 * - JWT
 * - Connect button
 * Nothing else.
 */
@Composable
fun LoginScreen(
    onConnected: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val token by viewModel.token.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    // Navigate to dashboard when Ready
    if (connectionState is ConnectionState.Ready) {
        onConnected()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sentinel Host",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = viewModel::onServerUrlChanged,
            label = { Text("Server URL") },
            placeholder = { Text("ws://192.168.1.100:8080/ws") },
            singleLine = true,
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = token,
            onValueChange = viewModel::onTokenChanged,
            label = { Text("JWT Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Connection state indicator
        if (isConnecting) {
            val stateText = when (connectionState) {
                is ConnectionState.Connecting -> "Connecting..."
                is ConnectionState.Connected -> "Connected"
                is ConnectionState.Authenticating -> "Authenticating..."
                is ConnectionState.Authenticated -> "Authenticated"
                is ConnectionState.Registering -> "Registering..."
                else -> "Working..."
            }
            Text(
                text = stateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Error message
        errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isConnecting) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = viewModel::connect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect")
            }
        }
    }
}
