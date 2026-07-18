package com.sentinel.admin.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Retrofit API interface for device endpoints.
 *
 * Uses the existing backend REST API.
 * Authentication via JWT Bearer token in Authorization header.
 */
interface DeviceApi {

    /**
     * GET /devices — Returns all connected devices with realtime state.
     */
    @GET("/devices")
    suspend fun getDevices(
        @Header("Authorization") authorization: String
    ): DevicesResponse

    /**
     * GET /devices/{deviceId} — Returns a single device by ID.
     */
    @GET("/devices/{deviceId}")
    suspend fun getDevice(
        @Header("Authorization") authorization: String,
        @Path("deviceId") deviceId: String
    ): DeviceDto
}
