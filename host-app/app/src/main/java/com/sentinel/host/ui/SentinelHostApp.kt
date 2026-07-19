package com.sentinel.host.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sentinel.host.domain.model.ConnectionState
import com.sentinel.host.ui.dashboard.DashboardScreen
import com.sentinel.host.ui.login.LoginViewModel
import com.sentinel.host.ui.theme.SentinelHostTheme

/**
 * Root composable for the Host app.
 * Auto-connect is handled by MainActivity.
 * This just shows connection status and the dashboard once ready.
 */
@Composable
fun SentinelHostApp() {
    SentinelHostTheme {
        val viewModel: LoginViewModel = hiltViewModel()
        val connectionState by viewModel.connectionState.collectAsState()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (connectionState) {
                is ConnectionState.Ready -> {
                    DashboardScreen(
                        onNavigateToSettings = {},
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                else -> {
                    StatusScreen(
                        state = connectionState,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
