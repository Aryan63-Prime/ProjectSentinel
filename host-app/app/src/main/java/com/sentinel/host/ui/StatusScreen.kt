package com.sentinel.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sentinel.host.domain.model.ConnectionState
import kotlinx.coroutines.delay

@Composable
fun StatusScreen(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    var showUpdatesChecked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3000)
        showUpdatesChecked = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Checking for updates",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!showUpdatesChecked) {
            CircularProgressIndicator(
                color = Color.White
            )
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (!showUpdatesChecked) "Checking..." else "Your device is up to date",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}
