package com.sentinel.host.domain.repository

/**
 * Sends periodic heartbeats to maintain the connection.
 */
interface HeartbeatRepository {
    suspend fun sendHeartbeat(): Result<Boolean>
}
