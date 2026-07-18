package com.sentinel.admin.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

/**
 * Map view for the Dashboard showing all device markers.
 *
 * - Green markers = online devices
 * - Red markers = offline devices
 * - Camera auto-fits all markers
 * - Marker click navigates to device detail
 */
@Composable
fun DashboardMapView(
    markers: List<DeviceMarker>,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (markers.isEmpty()) {
        NoLocationState(modifier = modifier)
        return
    }

    val cameraPositionState = rememberCameraPositionState()

    // Auto-fit camera to show all markers
    LaunchedEffect(markers) {
        if (markers.size == 1) {
            val pos = LatLng(markers[0].latitude, markers[0].longitude)
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(pos, 15f)
            )
        } else {
            val boundsBuilder = LatLngBounds.builder()
            markers.forEach { marker ->
                boundsBuilder.include(LatLng(marker.latitude, marker.longitude))
            }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100)
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        markers.forEach { marker ->
            val markerState = rememberMarkerState(
                key = marker.deviceId,
                position = LatLng(marker.latitude, marker.longitude)
            )

            // Update position when marker data changes
            LaunchedEffect(marker.latitude, marker.longitude) {
                markerState.position = LatLng(marker.latitude, marker.longitude)
            }

            Marker(
                state = markerState,
                title = marker.deviceName,
                snippet = marker.snippet,
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (marker.isOnline) {
                        BitmapDescriptorFactory.HUE_GREEN
                    } else {
                        BitmapDescriptorFactory.HUE_RED
                    }
                ),
                onClick = {
                    onMarkerClick(marker.deviceId)
                    true
                }
            )
        }
    }
}

@Composable
private fun NoLocationState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No locations available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Devices with location data will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
