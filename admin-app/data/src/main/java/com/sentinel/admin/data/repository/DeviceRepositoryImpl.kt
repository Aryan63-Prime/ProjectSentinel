package com.sentinel.admin.data.repository

import android.util.Log
import com.sentinel.admin.data.remote.api.DeviceApi
import com.sentinel.admin.data.remote.api.DeviceMapper.toDomain
import com.sentinel.admin.data.remote.protocol.DeviceUpdateDataJson
import com.sentinel.admin.data.remote.protocol.DeviceUpdateEventMapper
import com.sentinel.admin.data.remote.protocol.DeviceUpdateMessageJson
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceLocation
import com.sentinel.admin.domain.model.DeviceUpdateEvent
import com.sentinel.admin.domain.model.EventStatistics
import com.sentinel.admin.domain.repository.AuthRepository
import com.sentinel.admin.domain.repository.ConnectionRepository
import com.sentinel.admin.domain.repository.DeviceRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import retrofit2.HttpException
import java.io.IOException

/**
 * REST + WebSocket hybrid implementation of [DeviceRepository].
 *
 * - REST provides the initial device snapshot via [getDevices]/[getDevice]
 * - WebSocket DEVICE_UPDATE events apply incremental O(1) patches
 * - Maintains an immutable [Map] keyed by deviceId
 * - Tracks per-device sequence numbers for ordering
 * - Suppresses duplicate and stale events
 *
 * DeviceRepository receives updates directly from ConnectionRepository
 * (not through AdminSupervisor — keeping supervisor focused on connection lifecycle).
 */
class DeviceRepositoryImpl(
    private val deviceApi: DeviceApi,
    private val authRepository: AuthRepository,
    private val connectionRepository: ConnectionRepository,
    private val eventMapper: DeviceUpdateEventMapper,
    private val moshi: Moshi,
    scope: CoroutineScope
) : DeviceRepository {

    companion object {
        private const val TAG = "Sentinel:DeviceRepo"
    }

    // ============================================================
    // State
    // ============================================================

    private val _devices = MutableStateFlow<Map<String, Device>>(emptyMap())
    override val devices: StateFlow<Map<String, Device>> = _devices.asStateFlow()

    private val _deviceUpdates = MutableSharedFlow<DeviceUpdateEvent>(extraBufferCapacity = 64)
    override val deviceUpdates: SharedFlow<DeviceUpdateEvent> = _deviceUpdates.asSharedFlow()

    private val _eventStatistics = MutableStateFlow(EventStatistics())
    override val eventStatistics: StateFlow<EventStatistics> = _eventStatistics.asStateFlow()

    /** Per-device last sequence for ordering. */
    private val lastSequence = mutableMapOf<String, Long>()

    private val deviceUpdateMessageAdapter by lazy {
        moshi.adapter(DeviceUpdateMessageJson::class.java)
    }

    // ============================================================
    // Init — subscribe to WebSocket events
    // ============================================================

    init {
        connectionRepository.events
            .onEach { event ->
                when (event) {
                    is ConnectionEvent.DeviceUpdateReceived -> handleDeviceUpdate(event.rawJson)
                    else -> { /* Not our concern */ }
                }
            }
            .launchIn(scope)
    }

    // ============================================================
    // REST methods (unchanged behavior)
    // ============================================================

    override suspend fun getDevices(): Result<List<Device>> {
        return try {
            val token = authRepository.getToken()
                ?: return Result.failure(IllegalStateException("Not authenticated"))

            val response = deviceApi.getDevices("Bearer $token")
            val deviceList = response.devices.map { it.toDomain() }
                .filter { it.registered }  // Exclude admin's own session (unregistered)

            // Populate the live map from REST snapshot
            val deviceMap = deviceList.associateBy { it.deviceId }
            _devices.value = deviceMap
            // Reset sequence tracking on full refresh
            lastSequence.clear()
            _eventStatistics.update { it.copy(reconnectResyncs = it.reconnectResyncs + 1) }

            Result.success(deviceList)
        } catch (e: HttpException) {
            Result.failure(mapHttpError(e))
        } catch (e: IOException) {
            Result.failure(IOException("Network error: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDevice(deviceId: String): Result<Device> {
        return try {
            val token = authRepository.getToken()
                ?: return Result.failure(IllegalStateException("Not authenticated"))

            val dto = deviceApi.getDevice("Bearer $token", deviceId)
            val device = dto.toDomain()

            // Update live map with single device refresh
            _devices.update { current -> current + (deviceId to device) }

            Result.success(device)
        } catch (e: HttpException) {
            Result.failure(mapHttpError(e))
        } catch (e: IOException) {
            Result.failure(IOException("Network error: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // WebSocket event handling
    // ============================================================

    private fun handleDeviceUpdate(rawJson: String) {
        _eventStatistics.update { it.copy(received = it.received + 1) }

        val message = try {
            deviceUpdateMessageAdapter.fromJson(rawJson)
        } catch (e: Exception) {
            Log.w(TAG, "Malformed DEVICE_UPDATE payload, ignoring", e)
            _eventStatistics.update { it.copy(ignored = it.ignored + 1) }
            return
        }

        if (message == null) {
            _eventStatistics.update { it.copy(ignored = it.ignored + 1) }
            return
        }

        val sequence = message.sequence
        val deviceId = message.data.deviceId

        // Sequence ordering — reject stale/duplicate
        if (deviceId.isNotBlank()) {
            val lastSeq = lastSequence[deviceId] ?: -1
            if (sequence > 0 && lastSeq >= 0) {
                when {
                    sequence == lastSeq -> {
                        _eventStatistics.update { it.copy(duplicates = it.duplicates + 1) }
                        return
                    }
                    sequence < lastSeq -> {
                        _eventStatistics.update { it.copy(stale = it.stale + 1) }
                        return
                    }
                }
            }
            if (sequence > 0) {
                lastSequence[deviceId] = sequence
            }
        }

        // Map to domain event
        val domainEvent = eventMapper.map(message.data)
        if (domainEvent == null) {
            _eventStatistics.update { it.copy(ignored = it.ignored + 1) }
            return
        }

        // Apply incremental patch
        applyEvent(domainEvent)
        _eventStatistics.update { it.copy(applied = it.applied + 1) }
        _deviceUpdates.tryEmit(domainEvent)
    }

    /**
     * Applies a domain event as an O(1) patch to the device map.
     * Disconnected devices are marked offline (not removed).
     */
    private fun applyEvent(event: DeviceUpdateEvent) {
        when (event) {
            is DeviceUpdateEvent.DeviceConnected -> {
                _devices.update { current ->
                    val existing = current[event.deviceId]
                    val patched = existing?.copy(
                        heartbeatStatus = "online",
                        deviceName = event.deviceName ?: existing.deviceName,
                        appVersion = event.appVersion ?: existing.appVersion,
                        model = event.model ?: existing.model
                    ) ?: createMinimalDevice(event)
                    current + (event.deviceId to patched)
                }
            }

            is DeviceUpdateEvent.DeviceDisconnected -> {
                _devices.update { current ->
                    val existing = current[event.deviceId] ?: return@update current
                    current + (event.deviceId to existing.copy(heartbeatStatus = "offline"))
                }
            }

            is DeviceUpdateEvent.HeartbeatReceived -> {
                _devices.update { current ->
                    val existing = current[event.deviceId] ?: return@update current
                    current + (event.deviceId to existing.copy(
                        heartbeatStatus = "online",
                        lastHeartbeat = event.timestamp ?: existing.lastHeartbeat
                    ))
                }
            }

            is DeviceUpdateEvent.LocationUpdated -> {
                _devices.update { current ->
                    val existing = current[event.deviceId] ?: return@update current
                    val currentLocation = existing.latestLocation
                    val location = if (currentLocation != null) {
                        currentLocation.copy(
                            latitude = event.latitude,
                            longitude = event.longitude,
                            accuracy = event.accuracy ?: currentLocation.accuracy,
                            battery = event.battery ?: currentLocation.battery,
                            network = event.network ?: currentLocation.network
                        )
                    } else {
                        DeviceLocation(
                            deviceId = event.deviceId,
                            latitude = event.latitude,
                            longitude = event.longitude,
                            accuracy = event.accuracy ?: 0.0,
                            battery = event.battery ?: -1,
                            network = event.network ?: "unknown",
                            recordedAt = ""
                        )
                    }
                    current + (event.deviceId to existing.copy(latestLocation = location))
                }
            }

            is DeviceUpdateEvent.BatteryUpdated -> {
                _devices.update { current ->
                    val existing = current[event.deviceId] ?: return@update current
                    val location = existing.latestLocation?.copy(battery = event.battery)
                        ?: return@update current
                    current + (event.deviceId to existing.copy(latestLocation = location))
                }
            }

            is DeviceUpdateEvent.NetworkUpdated -> {
                _devices.update { current ->
                    val existing = current[event.deviceId] ?: return@update current
                    val location = existing.latestLocation?.copy(network = event.network)
                        ?: return@update current
                    current + (event.deviceId to existing.copy(latestLocation = location))
                }
            }

            is DeviceUpdateEvent.MetadataUpdated -> {
                _devices.update { current ->
                    val existing = current[event.deviceId] ?: return@update current
                    current + (event.deviceId to existing.copy(
                        deviceName = event.deviceName ?: existing.deviceName,
                        appVersion = event.appVersion ?: existing.appVersion,
                        model = event.model ?: existing.model
                    ))
                }
            }
        }
    }

    /**
     * Creates a minimal device entry when a "connected" event arrives
     * for a device not yet in the map (e.g., connected after initial REST load).
     */
    private fun createMinimalDevice(event: DeviceUpdateEvent.DeviceConnected): Device {
        return Device(
            deviceId = event.deviceId,
            connectionId = "",
            authenticated = true,
            registered = true,
            registrationState = "registered",
            heartbeatStatus = "online",
            connectedAt = "",
            lastHeartbeat = "",
            deviceName = event.deviceName ?: event.deviceId,
            appVersion = event.appVersion ?: "",
            model = event.model ?: "",
            latestLocation = null
        )
    }

    private fun mapHttpError(e: HttpException): Exception {
        return when (e.code()) {
            401 -> IllegalStateException("Unauthorized — invalid or expired token")
            403 -> IllegalStateException("Forbidden")
            404 -> NoSuchElementException("Device not found")
            500 -> RuntimeException("Server error")
            else -> RuntimeException("HTTP ${e.code()}: ${e.message()}")
        }
    }
}
