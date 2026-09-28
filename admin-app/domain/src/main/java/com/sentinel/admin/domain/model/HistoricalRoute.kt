package com.sentinel.admin.domain.model

data class BreadcrumbPoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val timestamp: Long,
    val accuracy: Float
)

data class DwellCluster(
    val latitude: Double,
    val longitude: Double,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Long
)

data class GeofenceZone(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float
)
