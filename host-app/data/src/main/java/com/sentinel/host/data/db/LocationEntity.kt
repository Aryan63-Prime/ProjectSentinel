package com.sentinel.host.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val battery: Int = 0,
    val network: String = "",
    val timestamp: Long = System.currentTimeMillis() / 1000,
    val provider: String = "fused"
)
