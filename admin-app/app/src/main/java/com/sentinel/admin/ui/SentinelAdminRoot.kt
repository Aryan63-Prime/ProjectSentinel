package com.sentinel.admin.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sentinel.admin.ui.navigation.AdminNavGraph
import com.sentinel.admin.ui.theme.SentinelAdminTheme

/**
 * Root composable for the Admin application.
 *
 * Applies the Material 3 theme and hosts the navigation graph.
 */
@Composable
fun SentinelAdminRoot() {
    SentinelAdminTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AdminNavGraph()
        }
    }
}
