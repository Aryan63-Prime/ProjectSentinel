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
        val serial = Build.SERIAL

        return DeviceInfo(
            deviceId = serial?.takeIf { it != Build.UNKNOWN }
                ?: "$manufacturer-$model".replace(" ", "-"),
            deviceName = "$manufacturer $model",
            model = model,
            appVersion = appVersion
        )
    }
}

class RegistrationException(val code: Int, override val message: String) : Exception(message)
