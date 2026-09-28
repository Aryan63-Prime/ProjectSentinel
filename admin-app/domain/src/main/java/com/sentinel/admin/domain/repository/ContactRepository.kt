package com.sentinel.admin.domain.repository

import com.sentinel.admin.domain.model.DeviceContactBook
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing device contacts synced from monitored hosts.
 */
interface ContactRepository {

    /**
     * Observes the contact book for a specific device.
     * Emits null if no contacts have been synced yet.
     */
    fun getContactBook(deviceId: String): Flow<DeviceContactBook?>

    /**
     * Persists the synchronized contact book for a device.
     */
    suspend fun saveContactBook(deviceId: String, contactBook: DeviceContactBook): Result<Unit>
}
