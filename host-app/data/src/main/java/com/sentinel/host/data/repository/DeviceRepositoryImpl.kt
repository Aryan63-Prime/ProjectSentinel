package com.sentinel.host.data.repository

import android.os.Build
import com.sentinel.host.data.remote.SequenceGenerator
import com.sentinel.host.data.remote.protocol.MessageSerializer
import com.sentinel.host.domain.model.ConnectionEvent
import com.sentinel.host.domain.model.DeviceInfo
import com.sentinel.host.domain.repository.ConnectionRepository
import com.sentinel.host.domain.repository.DeviceRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Sends REGISTER message and awaits REGISTER_ACK event with timeout.
 * Collects device info from Android system properties.
 */
class DeviceRepositoryImpl(
    private val connectionRepository: ConnectionRepository,
    private val messageSerializer: MessageSerializer,
    private val sequenceGenerator: SequenceGenerator,
    private val sessionManager: com.sentinel.host.domain.session.SessionManager,
    private val appVersion: String
) : DeviceRepository {

    companion object {
        private const val REGISTER_TIMEOUT_MS = 10_000L
    }

    override suspend fun register(device: DeviceInfo): Result<Boolean> {
        val json = messageSerializer.serializeRegister(
            deviceId = device.deviceId,
            deviceName = device.deviceName,
            appVersion = device.appVersion,
            model = device.model,
            sequence = sequenceGenerator.next()
        )

        if (!connectionRepository.sendText(json)) {
            return Result.failure(Exception("Failed to send REGISTER message"))
        }

        return try {
            withTimeout(REGISTER_TIMEOUT_MS) {
                val event = connectionRepository.events.first { event ->
                    event is ConnectionEvent.Registered || event is ConnectionEvent.Error
                }
                when (event) {
                    is ConnectionEvent.Registered -> Result.success(true)
                    is ConnectionEvent.Error -> Result.failure(
                        RegistrationException(event.code, event.message)
                    )
                    else -> Result.failure(Exception("Unexpected event"))
                }
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(RegistrationException(0, "Registration timeout"))
        }
    }

    override fun getDeviceInfo(): DeviceInfo {
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val model = Build.MODEL ?: "Unknown"

        // Use the device_id from the JWT token if available.
        // This ensures the REGISTER deviceId matches the AUTH deviceId,
        // preventing 403 Forbidden from the server's mismatch check.
        val jwtDeviceId = extractDeviceIdFromToken()

        val deviceId = jwtDeviceId
            ?: Build.SERIAL?.takeIf { it != Build.UNKNOWN }
            ?: "$manufacturer-$model".replace(" ", "-")

        return DeviceInfo(
            deviceId = deviceId,
            deviceName = "$manufacturer $model",
            model = model,
            appVersion = appVersion
        )
    }

    /**
     * Extracts the device_id claim from the saved JWT token.
     * JWT is base64-encoded: header.payload.signature
     * We decode the payload to get the device_id claim.
     */
    private fun extractDeviceIdFromToken(): String? {
        return try {
            val token = sessionManager.getToken() ?: return null
            val parts = token.split(".")
            if (parts.size != 3) return null
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
                Charsets.UTF_8
            )
            // Simple JSON parse for device_id
            val regex = """"device_id"\s*:\s*"([^"]+)"""".toRegex()
            regex.find(payload)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }
}

class RegistrationException(val code: Int, override val message: String) : Exception(message)
