package com.sentinel.host.domain.repository

import com.sentinel.host.domain.model.LocationUpdate

/**
 * Sends location updates to the server.
 * Fire-and-forget — no ACK expected per PROTOCOL.md.
 */
interface LocationRepository {
    suspend fun sendLocation(location: LocationUpdate)
}
