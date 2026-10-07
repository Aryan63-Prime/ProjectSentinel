package com.sentinel.admin.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.sentinel.admin.data.remote.api.DeviceApi
import com.sentinel.admin.data.remote.api.DeviceMapper.toDomain
import com.sentinel.admin.data.remote.protocol.DeviceUpdateDataJson
import com.sentinel.admin.data.remote.protocol.DeviceUpdateEventMapper
import com.sentinel.admin.data.remote.protocol.DeviceUpdateMessageJson
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.ConnectionState
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceLocation
import com.sentinel.admin.domain.model.DeviceUpdateEvent
import com.sentinel.admin.domain.model.EventStatistics
import com.sentinel.admin.domain.repository.AuthRepository
import com.sentinel.admin.domain.repository.ConnectionRepository
import com.sentinel.admin.domain.repository.DeviceRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

/**
 * REST + WebSocket hybrid implementation of [DeviceRepository].
 *
 * - REST provides the initial device snapshot via [getDevices]/[getDevice]
 * - WebSocket DEVICE_UPDATE events apply incremental O(1) patches
 * - Maintains an immutable [Map] keyed by deviceId / uniqueKey
 * - Tracks per-device sequence numbers for ordering
 * - Suppresses duplicate and stale events
 * - Provides persistent local storage cache to avoid UI flickering or lost telemetry across refreshes
 */
class DeviceRepositoryImpl(
    private val deviceApi: DeviceApi,
    private val authRepository: AuthRepository,
    private val connectionRepository: ConnectionRepository,
    private val eventMapper: DeviceUpdateEventMapper,
    private val moshi: Moshi,
    scope: CoroutineScope,
    private val context: Context? = null
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

    /** Track devices that report dedicated telemetry so generic server broadcasts don't clobber them. */
    private val dedicatedTelemetryDevices = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    data class DedicatedSnapshot(
        val deviceKey: String,
        val latitude: Double,
        val longitude: Double,
        val battery: Int,
        val network: String,
        val timestamp: Long
    )

    private val recentDedicatedSnapshots = java.util.concurrent.ConcurrentLinkedQueue<DedicatedSnapshot>()

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences("sentinel_device_cache", Context.MODE_PRIVATE)
    }

    private fun serializeDevices(devices: Collection<Device>): String {
        return try {
            val arr = JSONArray()
            for (d in devices.distinctBy { it.uniqueKey }) {
                val obj = JSONObject().apply {
                    put("deviceId", d.deviceId)
                    put("connectionId", d.connectionId)
                    put("authenticated", d.authenticated)
                    put("registered", d.registered)
                    put("registrationState", d.registrationState)
                    put("heartbeatStatus", d.heartbeatStatus)
                    put("connectedAt", d.connectedAt)
                    put("lastHeartbeat", d.lastHeartbeat)
                    put("deviceName", d.deviceName)
                    put("appVersion", d.appVersion)
                    put("model", d.model)
                    val loc = d.latestLocation
                    if (loc != null) {
                        val locObj = JSONObject().apply {
                            put("deviceId", loc.deviceId)
                            put("latitude", loc.latitude)
                            put("longitude", loc.longitude)
                            put("accuracy", loc.accuracy)
                            put("battery", loc.battery)
                            put("network", loc.network)
                            put("recordedAt", loc.recordedAt)
                        }
                        put("latestLocation", locObj)
                    }
                }
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Error serializing device cache: ${e.message}")
            ""
        }
    }

    private fun deserializeDevices(jsonStr: String): List<Device> {
        val list = mutableListOf<Device>()
        if (jsonStr.isBlank()) return list
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val locObj = obj.optJSONObject("latestLocation")
                val loc = if (locObj != null) {
                    DeviceLocation(
                        deviceId = locObj.optString("deviceId", obj.getString("deviceId")),
                        latitude = locObj.optDouble("latitude", 0.0),
                        longitude = locObj.optDouble("longitude", 0.0),
                        accuracy = locObj.optDouble("accuracy", 0.0),
                        battery = locObj.optInt("battery", -1),
                        network = locObj.optString("network", "unknown"),
                        recordedAt = locObj.optString("recordedAt", "")
                    )
                } else null

                list.add(
                    Device(
                        deviceId = obj.getString("deviceId"),
                        connectionId = obj.optString("connectionId", ""),
                        authenticated = obj.optBoolean("authenticated", true),
                        registered = obj.optBoolean("registered", true),
                        registrationState = obj.optString("registrationState", "registered"),
                        heartbeatStatus = obj.optString("heartbeatStatus", "online"),
                        connectedAt = obj.optString("connectedAt", ""),
                        lastHeartbeat = obj.optString("lastHeartbeat", ""),
                        deviceName = obj.optString("deviceName", ""),
                        appVersion = obj.optString("appVersion", ""),
                        model = obj.optString("model", ""),
                        latestLocation = loc
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize cached devices: ${e.message}")
        }
        return list
    }

    private fun saveDeviceCache(devices: Map<String, Device>) {
        try {
            val serialized = serializeDevices(devices.values)
            if (serialized.isNotBlank()) {
                prefs?.edit()?.putString("cached_devices", serialized)?.apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save device cache: ${e.message}")
        }
    }

    private fun loadDeviceCache(): Map<String, Device> {
        val saved = prefs?.getString("cached_devices", null) ?: return emptyMap()
        val list = deserializeDevices(saved)
        val map = LinkedHashMap<String, Device>()
        for (d in list) {
            map[d.uniqueKey] = d
            if (d.connectionId.isNotBlank()) map[d.connectionId] = d
            if (!map.containsKey(d.deviceId)) map[d.deviceId] = d
        }
        return map
    }

    private fun isEchoOfDedicatedTelemetry(lat: Double, lng: Double, battery: Int?): Boolean {
        val now = System.currentTimeMillis()
        recentDedicatedSnapshots.removeIf { now - it.timestamp > 120_000L }

        return recentDedicatedSnapshots.any { snap ->
            val hasCoords = (lat != 0.0 && lng != 0.0 && snap.latitude != 0.0 && snap.longitude != 0.0)
            val coordsMatch = hasCoords && (Math.abs(snap.latitude - lat) < 0.0001 && Math.abs(snap.longitude - lng) < 0.0001)
            val hasBattery = (battery != null && battery >= 0 && snap.battery >= 0)
            val batteryMatch = hasBattery && (battery == snap.battery)

            if (hasBattery && !batteryMatch) {
                // Different battery percentage -> definitely NOT an echo of this snapshot
                false
            } else if (coordsMatch && batteryMatch) {
                true
            } else if (coordsMatch && !hasBattery) {
                true
            } else if (batteryMatch && !hasCoords) {
                true
            } else {
                false
            }
        }
    }

    private fun isEchoOfDedicatedBattery(battery: Int): Boolean {
        val now = System.currentTimeMillis()
        recentDedicatedSnapshots.removeIf { now - it.timestamp > 120_000L }
        return recentDedicatedSnapshots.any { it.battery == battery }
    }

    private fun isEchoOfDedicatedNetwork(network: String): Boolean {
        val now = System.currentTimeMillis()
        recentDedicatedSnapshots.removeIf { now - it.timestamp > 120_000L }
        return recentDedicatedSnapshots.any { it.network.equals(network, ignoreCase = true) }
    }

    private fun queryDevicesSystemInfo() {
        try {
            val ts = System.currentTimeMillis() / 1000
            val seq = System.currentTimeMillis()
            val cmdJson = """{"type":"COMMAND","version":1,"timestamp":$ts,"sequence":$seq,"data":{"targetDeviceId":"HOST-001","command":"GET_SYSTEM_INFO","params":{}}}"""
            connectionRepository.sendText(cmdJson)

            // Also send targeted requests for all distinct known devices
            val knownKeys = _devices.value.values.map { it.uniqueKey }.distinct()
            for (key in knownKeys) {
                if (key != "HOST-001") {
                    val targetedJson = """{"type":"COMMAND","version":1,"timestamp":$ts,"sequence":$seq,"data":{"targetDeviceId":"$key","command":"GET_SYSTEM_INFO","params":{"targetUniqueKey":"$key"}}}"""
                    connectionRepository.sendText(targetedJson)
                }
            }
        } catch (_: Exception) {}
    }

    private val deviceUpdateMessageAdapter by lazy {
        moshi.adapter(DeviceUpdateMessageJson::class.java)
    }

    // ============================================================
    // Init — subscribe to WebSocket events
    // ============================================================

    init {
        val cached = loadDeviceCache()
        if (cached.isNotEmpty()) {
            _devices.value = cached
            Log.i(TAG, "Initialized DeviceRepository with ${cached.values.distinctBy { it.uniqueKey }.size} cached devices: ${cached.values.distinctBy { it.uniqueKey }.map { "${it.model}(${it.latestLocation?.battery}%)" }}")
        }

        connectionRepository.events
            .onEach { event ->
                when (event) {
                    is ConnectionEvent.DeviceUpdateReceived -> handleDeviceUpdate(event.rawJson)
                    is ConnectionEvent.CommandResultReceived -> handleCommandResult(event.rawJson)
                    else -> { /* Not our concern */ }
                }
            }
            .launchIn(scope)

        connectionRepository.state
            .onEach { state ->
                if (state is ConnectionState.Ready) {
                    queryDevicesSystemInfo()
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
                .filter { it.registered && it.deviceId.isNotBlank() }

            // Populate live map from REST snapshot keyed by unique device key (model/connectionId)
            val currentMap = _devices.value
            val deviceMap = LinkedHashMap<String, Device>(currentMap)
            for (dev in deviceList) {
                val existing = currentMap[dev.uniqueKey] ?: currentMap[dev.connectionId]

                val serverLoc = dev.latestLocation
                val isEchoOfAnotherDevice = serverLoc != null && recentDedicatedSnapshots.any { snap ->
                    snap.deviceKey != dev.uniqueKey &&
                    snap.battery >= 0 && snap.battery == serverLoc.battery &&
                    (serverLoc.latitude == 0.0 || Math.abs(snap.latitude - serverLoc.latitude) < 0.0001)
                }

                val resolvedLocation = existing?.latestLocation ?: if (!isEchoOfAnotherDevice) serverLoc else null

                val merged = dev.copy(
                    latestLocation = resolvedLocation,
                    heartbeatStatus = if (existing?.heartbeatStatus == "online") "online" else dev.heartbeatStatus
                )

                deviceMap[merged.uniqueKey] = merged
                if (merged.connectionId.isNotBlank()) {
                    deviceMap[merged.connectionId] = merged
                }
                if (!deviceMap.containsKey(merged.deviceId)) {
                    deviceMap[merged.deviceId] = merged
                }
            }
            _devices.value = deviceMap
            saveDeviceCache(deviceMap)
            // Reset sequence tracking on full refresh
            lastSequence.clear()
            _eventStatistics.update { it.copy(reconnectResyncs = it.reconnectResyncs + 1) }

            queryDevicesSystemInfo()

            Result.success(deviceMap.values.distinctBy { it.uniqueKey })
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

            val cached = _devices.value[deviceId]
                ?: _devices.value.values.find { it.uniqueKey == deviceId || it.connectionId == deviceId || it.deviceId == deviceId }

            val serverDeviceId = if (deviceId.contains("_")) deviceId.substringBefore("_") else deviceId
            val dto = try {
                deviceApi.getDevice("Bearer $token", serverDeviceId)
            } catch (e: Exception) {
                if (cached != null) return Result.success(cached)
                throw e
            }
            val device = dto.toDomain()

            if (cached != null && device.model.isNotBlank() && !device.model.equals(cached.model, ignoreCase = true)) {
                // The server returned another device sharing the same deviceId token.
                // Do not clobber cached device with the other device's snapshot.
                return Result.success(cached)
            }

            val targetKey = cached?.uniqueKey ?: device.uniqueKey

            val serverLoc = device.latestLocation
            val isEchoOfAnotherDevice = serverLoc != null && recentDedicatedSnapshots.any { snap ->
                snap.deviceKey != targetKey &&
                ((snap.battery >= 0 && snap.battery == serverLoc.battery) ||
                 (serverLoc.latitude != 0.0 && Math.abs(snap.latitude - serverLoc.latitude) < 0.0005 && Math.abs(snap.longitude - serverLoc.longitude) < 0.0005))
            }
            val safeServerLoc = if (isEchoOfAnotherDevice) null else serverLoc

            // Update live map with single device refresh, safely preserving established name/model if server returned blank
            _devices.update { current ->
                val existing = current[targetKey] ?: current[deviceId]
                val merged = if (existing != null && existing.registered && !device.registered) {
                    existing.copy(
                        heartbeatStatus = device.heartbeatStatus,
                        lastHeartbeat = if (device.lastHeartbeat.isNotBlank()) device.lastHeartbeat else existing.lastHeartbeat,
                        latestLocation = existing.latestLocation ?: safeServerLoc
                    )
                } else if (existing != null) {
                    existing.copy(
                        heartbeatStatus = device.heartbeatStatus,
                        lastHeartbeat = if (device.lastHeartbeat.isNotBlank()) device.lastHeartbeat else existing.lastHeartbeat,
                        latestLocation = existing.latestLocation ?: safeServerLoc,
                        deviceName = if (device.deviceName.isNotBlank()) device.deviceName else existing.deviceName,
                        model = if (device.model.isNotBlank()) device.model else existing.model
                    )
                } else {
                    device.copy(latestLocation = safeServerLoc)
                }
                val updated = current.toMutableMap()
                updated[targetKey] = merged
                if (merged.connectionId.isNotBlank()) updated[merged.connectionId] = merged
                if (!updated.containsKey(merged.deviceId)) updated[merged.deviceId] = merged
                saveDeviceCache(updated)
                updated
            }

            Result.success(_devices.value[targetKey] ?: cached ?: device)
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
                    val key = if (!event.model.isNullOrBlank()) {
                        "${event.deviceId}_${event.model}"
                    } else {
                        current.values.find { it.deviceId == event.deviceId }?.uniqueKey ?: event.deviceId
                    }
                    val existing = current[key] ?: current[event.deviceId]
                    val patched = existing?.copy(
                        heartbeatStatus = "online",
                        registered = true,
                        registrationState = "registered",
                        deviceName = event.deviceName?.takeIf { it.isNotBlank() } ?: existing.deviceName,
                        appVersion = event.appVersion?.takeIf { it.isNotBlank() } ?: existing.appVersion,
                        model = event.model?.takeIf { it.isNotBlank() } ?: existing.model
                    ) ?: createMinimalDevice(event)

                    val updated = current.toMutableMap()
                    updated[patched.uniqueKey] = patched
                    if (patched.connectionId.isNotBlank()) updated[patched.connectionId] = patched
                    val distinct = updated.values.filter { it.deviceId == patched.deviceId }.distinctBy { it.uniqueKey }
                    if (distinct.size <= 1 || updated[patched.deviceId]?.uniqueKey == patched.uniqueKey) {
                        updated[patched.deviceId] = patched
                    }
                    saveDeviceCache(updated)
                    updated
                }
            }

            is DeviceUpdateEvent.DeviceDisconnected -> {
                _devices.update { current ->
                    val distinctDevices = current.values.filter { it.deviceId == event.deviceId }.distinctBy { it.uniqueKey }
                    if (distinctDevices.size <= 1) {
                        val existing = current[event.deviceId] ?: return@update current
                        val patched = existing.copy(heartbeatStatus = "offline")
                        val updated = current.toMutableMap()
                        updated[patched.uniqueKey] = patched
                        if (patched.connectionId.isNotBlank()) updated[patched.connectionId] = patched
                        updated[event.deviceId] = patched
                        saveDeviceCache(updated)
                        updated
                    } else {
                        // When multiple devices share deviceId, server EventDisconnected without model
                        // could be from either phone. We do not mark both offline prematurely.
                        current
                    }
                }
            }

            is DeviceUpdateEvent.HeartbeatReceived -> {
                _devices.update { current ->
                    val matchingKeys = current.entries
                        .filter { it.value.deviceId == event.deviceId }
                        .map { it.key }
                    if (matchingKeys.isEmpty()) {
                        val existing = current[event.deviceId] ?: return@update current
                        current + (event.deviceId to existing.copy(
                            heartbeatStatus = "online",
                            lastHeartbeat = event.timestamp ?: existing.lastHeartbeat
                        ))
                    } else {
                        val updated = current.toMutableMap()
                        for (k in matchingKeys) {
                            updated[k] = updated[k]!!.copy(
                                heartbeatStatus = "online",
                                lastHeartbeat = event.timestamp ?: updated[k]!!.lastHeartbeat
                            )
                        }
                        saveDeviceCache(updated)
                        updated
                    }
                }
            }

            is DeviceUpdateEvent.LocationUpdated -> {
                _devices.update { current ->
                    val matchingKeys = current.entries
                        .filter { it.value.deviceId == event.deviceId }
                        .map { it.key }
                    if (matchingKeys.isEmpty()) {
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
                        val updated = current + (event.deviceId to existing.copy(latestLocation = location))
                        saveDeviceCache(updated)
                        updated
                    } else {
                        val distinctDevices = current.values.filter { it.deviceId == event.deviceId }.distinctBy { it.uniqueKey }
                        val targetKey: String? = if (!event.model.isNullOrBlank()) {
                            distinctDevices.find { it.model.equals(event.model, ignoreCase = true) }?.uniqueKey
                        } else if (distinctDevices.size == 1) {
                            distinctDevices.first().uniqueKey
                        } else {
                            if (isEchoOfDedicatedTelemetry(event.latitude, event.longitude, event.battery)) {
                                Log.d(TAG, "Suppressed echo of dedicated telemetry (lat=${event.latitude}, bat=${event.battery})")
                                null
                            } else {
                                val nonDedicated = distinctDevices.filter { !dedicatedTelemetryDevices.contains(it.uniqueKey) }
                                if (nonDedicated.size == 1) {
                                    nonDedicated.first().uniqueKey
                                } else {
                                    Log.d(TAG, "Suppressed ambiguous untagged LocationUpdated from server (devices: ${distinctDevices.map { it.model }})")
                                    null
                                }
                            }
                        }

                        if (targetKey == null) {
                            return@update current
                        }

                        val existing = current[targetKey] ?: return@update current
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
                        val updated = current.toMutableMap()
                        val patched = existing.copy(latestLocation = location)
                        updated[targetKey] = patched
                        if (patched.uniqueKey != targetKey) updated[patched.uniqueKey] = patched
                        if (patched.connectionId.isNotBlank()) updated[patched.connectionId] = patched
                        val distinct = updated.values.filter { it.deviceId == patched.deviceId }.distinctBy { it.uniqueKey }
                        if (distinct.size <= 1 || updated[patched.deviceId]?.uniqueKey == patched.uniqueKey) {
                            updated[patched.deviceId] = patched
                        }
                        saveDeviceCache(updated)
                        updated
                    }
                }
            }

            is DeviceUpdateEvent.BatteryUpdated -> {
                _devices.update { current ->
                    val matchingKeys = current.entries
                        .filter { it.value.deviceId == event.deviceId }
                        .map { it.key }
                    if (matchingKeys.isEmpty()) {
                        val existing = current[event.deviceId] ?: return@update current
                        val location = existing.latestLocation?.copy(battery = event.battery)
                            ?: return@update current
                        val updated = current + (event.deviceId to existing.copy(latestLocation = location))
                        saveDeviceCache(updated)
                        updated
                    } else {
                        val distinctDevices = current.values.filter { it.deviceId == event.deviceId }.distinctBy { it.uniqueKey }
                        val targetKey: String? = if (!event.model.isNullOrBlank()) {
                            distinctDevices.find { it.model.equals(event.model, ignoreCase = true) }?.uniqueKey
                        } else if (distinctDevices.size == 1) {
                            distinctDevices.first().uniqueKey
                        } else {
                            if (isEchoOfDedicatedTelemetry(0.0, 0.0, event.battery)) {
                                Log.d(TAG, "Suppressed echo of dedicated telemetry battery (${event.battery}%)")
                                null
                            } else {
                                val nonDedicated = distinctDevices.filter { !dedicatedTelemetryDevices.contains(it.uniqueKey) }
                                if (nonDedicated.size == 1) {
                                    nonDedicated.first().uniqueKey
                                } else {
                                    Log.d(TAG, "Suppressed untagged BatteryUpdated from server (${event.battery}%)")
                                    null
                                }
                            }
                        }

                        if (targetKey == null) return@update current

                        val existing = current[targetKey] ?: return@update current
                        val location = existing.latestLocation?.copy(battery = event.battery)
                            ?: DeviceLocation(
                                deviceId = existing.deviceId,
                                latitude = 0.0,
                                longitude = 0.0,
                                accuracy = 0.0,
                                battery = event.battery,
                                network = "unknown",
                                recordedAt = ""
                            )
                        val updated = current.toMutableMap()
                        val patched = existing.copy(latestLocation = location)
                        updated[targetKey] = patched
                        if (patched.uniqueKey != targetKey) updated[patched.uniqueKey] = patched
                        if (patched.connectionId.isNotBlank()) updated[patched.connectionId] = patched
                        val distinct = updated.values.filter { it.deviceId == patched.deviceId }.distinctBy { it.uniqueKey }
                        if (distinct.size <= 1 || updated[patched.deviceId]?.uniqueKey == patched.uniqueKey) {
                            updated[patched.deviceId] = patched
                        }
                        saveDeviceCache(updated)
                        updated
                    }
                }
            }

            is DeviceUpdateEvent.NetworkUpdated -> {
                _devices.update { current ->
                    val matchingKeys = current.entries
                        .filter { it.value.deviceId == event.deviceId }
                        .map { it.key }
                    if (matchingKeys.isEmpty()) {
                        val existing = current[event.deviceId] ?: return@update current
                        val location = existing.latestLocation?.copy(network = event.network)
                            ?: return@update current
                        val updated = current + (event.deviceId to existing.copy(latestLocation = location))
                        saveDeviceCache(updated)
                        updated
                    } else {
                        val distinctDevices = current.values.filter { it.deviceId == event.deviceId }.distinctBy { it.uniqueKey }
                        val targetKey: String? = if (!event.model.isNullOrBlank()) {
                            distinctDevices.find { it.model.equals(event.model, ignoreCase = true) }?.uniqueKey
                        } else if (distinctDevices.size == 1) {
                            distinctDevices.first().uniqueKey
                        } else {
                            val nonDedicated = distinctDevices.filter { !dedicatedTelemetryDevices.contains(it.uniqueKey) }
                            if (nonDedicated.size == 1) {
                                nonDedicated.first().uniqueKey
                            } else {
                                null
                            }
                        }

                        if (targetKey == null) return@update current

                        val existing = current[targetKey] ?: return@update current
                        val location = existing.latestLocation?.copy(network = event.network)
                            ?: DeviceLocation(
                                deviceId = existing.deviceId,
                                latitude = 0.0,
                                longitude = 0.0,
                                accuracy = 0.0,
                                battery = -1,
                                network = event.network,
                                recordedAt = ""
                            )
                        val updated = current.toMutableMap()
                        val patched = existing.copy(latestLocation = location)
                        updated[targetKey] = patched
                        if (patched.uniqueKey != targetKey) updated[patched.uniqueKey] = patched
                        if (patched.connectionId.isNotBlank()) updated[patched.connectionId] = patched
                        val distinct = updated.values.filter { it.deviceId == patched.deviceId }.distinctBy { it.uniqueKey }
                        if (distinct.size <= 1 || updated[patched.deviceId]?.uniqueKey == patched.uniqueKey) {
                            updated[patched.deviceId] = patched
                        }
                        saveDeviceCache(updated)
                        updated
                    }
                }
            }

            is DeviceUpdateEvent.MetadataUpdated -> {
                _devices.update { current ->
                    val matchingKeys = current.entries
                        .filter { it.value.deviceId == event.deviceId }
                        .map { it.key }
                    if (matchingKeys.isEmpty()) {
                        val existing = current[event.deviceId] ?: return@update current
                        val updated = current + (event.deviceId to existing.copy(
                            deviceName = event.deviceName ?: existing.deviceName,
                            appVersion = event.appVersion ?: existing.appVersion,
                            model = event.model ?: existing.model
                        ))
                        saveDeviceCache(updated)
                        updated
                    } else {
                        val updated = current.toMutableMap()
                        for (k in matchingKeys) {
                            val existing = updated[k]!!
                            updated[k] = existing.copy(
                                deviceName = event.deviceName ?: existing.deviceName,
                                appVersion = event.appVersion ?: existing.appVersion,
                                model = event.model ?: existing.model
                            )
                        }
                        saveDeviceCache(updated)
                        updated
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val mapAdapter by lazy {
        moshi.adapter(Map::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleCommandResult(rawJson: String) {
        try {
            val jsonMap = mapAdapter.fromJson(rawJson) as? Map<String, Any?> ?: return
            if (jsonMap["type"] != "COMMAND_RESULT") return
            val data = jsonMap["data"] as? Map<String, Any?> ?: return
            val command = data["command"] as? String
            val payload = data["payload"] as? Map<String, Any?> ?: return

            if (command == "TELEMETRY_REPORT" || (command == "GET_SYSTEM_INFO" && payload.containsKey("batteryPercent"))) {
                handleTelemetryReport(payload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse command result in DeviceRepository: ${e.message}", e)
        }
    }

    private fun handleTelemetryReport(payload: Map<String, Any?>) {
        val model = (payload["model"] as? String)?.takeIf { it.isNotBlank() }
        val deviceId = (payload["deviceId"] as? String)?.takeIf { it.isNotBlank() } ?: "HOST-001"
        val uniqueKey = (payload["uniqueKey"] as? String)?.takeIf { it.isNotBlank() }
            ?: if (model != null) "${deviceId}_$model" else null

        val battery = (payload["battery"] as? Number)?.toInt()
            ?: (payload["batteryPercent"] as? Number)?.toInt()

        val isCharging = payload["isCharging"] as? Boolean ?: false
        val wifiSsid = (payload["wifiSsid"] as? String) ?: ""
        val network = (payload["network"] as? String)?.takeIf { it.isNotBlank() }
            ?: if (isCharging) "WiFi (Charging)" else if (wifiSsid.isNotBlank()) "WiFi" else null

        val lat = (payload["latitude"] as? Number)?.toDouble()
        val lng = (payload["longitude"] as? Number)?.toDouble()
        val accuracy = (payload["accuracy"] as? Number)?.toDouble()

        _devices.update { current ->
            val targetKey = if (uniqueKey != null && current.containsKey(uniqueKey)) {
                uniqueKey
            } else if (model != null) {
                current.entries.find { it.value.model.equals(model, ignoreCase = true) }?.key
                    ?: "${deviceId}_$model"
            } else {
                val candidates = current.values.filter { it.deviceId == deviceId }.distinctBy { it.uniqueKey }
                if (candidates.size == 1) {
                    candidates.first().uniqueKey
                } else if (candidates.size > 1) {
                    candidates.find { !it.model.equals("I2401", ignoreCase = true) }?.uniqueKey
                        ?: candidates.first().uniqueKey
                } else {
                    null
                }
            }

            if (targetKey == null) {
                return@update current
            }

            val finalKey = targetKey
            if (battery != null || (lat != null && lng != null && lat != 0.0 && lng != 0.0)) {
                recentDedicatedSnapshots.add(
                    DedicatedSnapshot(
                        deviceKey = finalKey,
                        latitude = lat ?: 0.0,
                        longitude = lng ?: 0.0,
                        battery = battery ?: -1,
                        network = network ?: "",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            dedicatedTelemetryDevices.add(finalKey)
            if (uniqueKey != null) {
                dedicatedTelemetryDevices.add(uniqueKey)
            }
            if (model != null) {
                dedicatedTelemetryDevices.add("${deviceId}_$model")
            }

            val existing = current[targetKey]
            val prevLoc = existing?.latestLocation

            val updatedLocation = if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                (prevLoc ?: DeviceLocation(
                    deviceId = deviceId,
                    latitude = lat,
                    longitude = lng,
                    accuracy = accuracy ?: 0.0,
                    battery = battery ?: -1,
                    network = network ?: "unknown",
                    recordedAt = ""
                )).copy(
                    latitude = lat,
                    longitude = lng,
                    accuracy = accuracy ?: prevLoc?.accuracy ?: 0.0,
                    battery = battery ?: prevLoc?.battery ?: -1,
                    network = network ?: prevLoc?.network ?: "unknown"
                )
            } else if (battery != null || network != null) {
                prevLoc?.copy(
                    battery = battery ?: prevLoc.battery,
                    network = network ?: prevLoc.network
                ) ?: DeviceLocation(
                    deviceId = deviceId,
                    latitude = 0.0,
                    longitude = 0.0,
                    accuracy = 0.0,
                    battery = battery ?: -1,
                    network = network ?: "unknown",
                    recordedAt = ""
                )
            } else {
                prevLoc
            }

            val currentUtc = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())

            val patched = (existing ?: Device(
                deviceId = deviceId,
                connectionId = "",
                authenticated = true,
                registered = true,
                registrationState = "registered",
                heartbeatStatus = "online",
                connectedAt = "",
                lastHeartbeat = currentUtc,
                deviceName = model ?: deviceId,
                appVersion = "",
                model = model ?: "",
                latestLocation = updatedLocation
            )).copy(
                latestLocation = updatedLocation,
                heartbeatStatus = "online",
                lastHeartbeat = currentUtc,
                model = model ?: existing?.model ?: ""
            )

            val updated = current.toMutableMap()
            updated[targetKey] = patched
            if (patched.uniqueKey != targetKey) {
                updated[patched.uniqueKey] = patched
            }
            if (patched.connectionId.isNotBlank()) {
                updated[patched.connectionId] = patched
            }
            val distinct = updated.values.filter { it.deviceId == patched.deviceId }.distinctBy { it.uniqueKey }
            if (distinct.size <= 1 || updated[patched.deviceId]?.uniqueKey == patched.uniqueKey) {
                updated[patched.deviceId] = patched
            }
            saveDeviceCache(updated)
            updated
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
            deviceName = event.deviceName?.takeIf { it.isNotBlank() } ?: event.deviceId,
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
