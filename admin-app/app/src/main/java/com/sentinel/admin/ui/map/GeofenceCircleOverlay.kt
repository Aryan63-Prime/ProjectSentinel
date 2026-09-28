package com.sentinel.admin.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.sentinel.admin.domain.model.GeofenceZone

@Composable
fun GeofenceCircleOverlay(
    zones: List<GeofenceZone>
) {
    zones.forEach { zone ->
        val center = LatLng(zone.latitude, zone.longitude)

        Circle(
            center = center,
            radius = zone.radiusMeters.toDouble(),
            fillColor = Color(0x221E88E5), // Translucent Blue
            strokeColor = Color(0xFF1E88E5),
            strokeWidth = 3f
        )

        Marker(
            state = MarkerState(position = center),
            title = zone.id,
            snippet = "Radius: ${zone.radiusMeters}m",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        )
    }
}
