package com.sentinel.host.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sentinel.host.ui.dashboard.DashboardScreen
import com.sentinel.host.ui.login.LoginScreen
import com.sentinel.host.ui.permissions.PermissionScreen
import com.sentinel.host.ui.settings.SettingsScreen
import com.sentinel.host.ui.theme.SentinelHostTheme

@Composable
fun SentinelHostApp() {
    SentinelHostTheme {
        val navController = rememberNavController()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Permissions.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Permissions.route) {
                    PermissionScreen(
                        onAllGranted = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Permissions.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Login.route) {
                    LoginScreen(
                        onConnected = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    data object Permissions : Screen("permissions")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Settings : Screen("settings")
}

