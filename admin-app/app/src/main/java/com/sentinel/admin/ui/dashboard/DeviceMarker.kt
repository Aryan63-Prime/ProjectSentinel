package com.sentinel.admin.ui.dashboard

/**
 * Pure data class representing a map marker for the Dashboard map view.
 *
 * No Android map objects — only domain data.
 * The Compose layer converts these to Google Maps markers.
 */
data class DeviceMarker(
    val deviceId: String,
    val deviceName: String,
    val latitude: Double,
    val longitude: Double,
    val isOnline: Boolean,
    val battery: Int,
    val network: String
) {
    /** Marker snippet text for the info window. */
    val snippet: String
        get() = "$deviceId · ${battery}% · $network"
}
