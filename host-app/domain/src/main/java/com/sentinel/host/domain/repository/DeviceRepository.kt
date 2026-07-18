package com.sentinel.host.domain.repository

import com.sentinel.host.domain.model.DeviceInfo

/**
 * Manages device registration with the server.
 */
interface DeviceRepository {
    suspend fun register(device: DeviceInfo): Result<Boolean>
    fun getDeviceInfo(): DeviceInfo
}
