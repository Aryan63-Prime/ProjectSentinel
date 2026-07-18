package com.sentinel.admin.data.remote.api

import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceLocation

/**
 * Maps REST API DTOs → domain models.
 *
 * DTOs stay inside :data. Domain models are returned to callers.
 * No logic — pure structural mapping.
 */
object DeviceMapper {

    fun DeviceDto.toDomain(): Device = Device(
        deviceId = deviceId,
        connectionId = connectionId,
        authenticated = authenticated,
        registered = registered,
        registrationState = registrationState,
        heartbeatStatus = heartbeatStatus,
        connectedAt = connectedAt,
        lastHeartbeat = lastHeartbeat,
        deviceName = deviceName,
        appVersion = appVersion,
        model = model,
        latestLocation = latestLocation?.toDomain()
    )

    fun DeviceLocationDto.toDomain(): DeviceLocation = DeviceLocation(
        deviceId = deviceId,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        battery = battery,
        network = network,
        recordedAt = recordedAt
    )
}
