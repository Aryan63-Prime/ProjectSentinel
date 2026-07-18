package com.sentinel.admin.domain.repository

import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceUpdateEvent
import com.sentinel.admin.domain.model.EventStatistics
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for device data.
 *
 * Combines REST snapshots with incremental WebSocket updates.
 * REST provides the initial load; WebSocket applies O(1) patches.
 */
interface DeviceRepository {
    /** Fetches all connected devices via REST. */
    suspend fun getDevices(): Result<List<Device>>

    /** Fetches a single device by ID via REST. */
    suspend fun getDevice(deviceId: String): Result<Device>

    /** Live device map, merging REST snapshot + WebSocket incremental updates. */
    val devices: StateFlow<Map<String, Device>>

    /** Stream of domain events from WebSocket DEVICE_UPDATE messages. */
    val deviceUpdates: SharedFlow<DeviceUpdateEvent>

    /** Debug statistics for event processing. */
    val eventStatistics: StateFlow<EventStatistics>
}
