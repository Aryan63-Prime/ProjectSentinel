package com.sentinel.admin.data.repository

import com.sentinel.admin.data.remote.api.DeviceApi
import com.sentinel.admin.data.remote.api.DeviceDto
import com.sentinel.admin.data.remote.api.DevicesResponse
import com.sentinel.admin.data.remote.protocol.DeviceUpdateDataJson
import com.sentinel.admin.data.remote.protocol.DeviceUpdateEventMapper
import com.sentinel.admin.data.remote.protocol.DeviceUpdateMessageJson
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.ConnectionState
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceLocation
import com.sentinel.admin.domain.repository.ConnectionRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [DeviceRepositoryImpl] live WebSocket update handling.
 *
 * Seeds the device map by emitting "connected" events (not reflection),
 * then tests incremental patching, duplicate/stale suppression,
 * and offline marking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepositoryImplLiveUpdateTest {

    private lateinit var scope: TestScope
    private lateinit var eventsFlow: MutableSharedFlow<ConnectionEvent>
    private lateinit var repo: DeviceRepositoryImpl
    private lateinit var moshi: Moshi
    private val messageAdapter by lazy { moshi.adapter(DeviceUpdateMessageJson::class.java) }

    /** Stub DeviceApi — REST methods are not tested here. */
    private val stubDeviceApi = object : DeviceApi {
        override suspend fun getDevices(authorization: String): DevicesResponse =
            DevicesResponse(emptyList())
        override suspend fun getDevice(authorization: String, deviceId: String): DeviceDto =
            throw NotImplementedError("not used in live update tests")
    }

    @Before
    fun setUp() {
        scope = TestScope(UnconfinedTestDispatcher())
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        moshi = Moshi.Builder().build()

        val fakeConnectionRepo = object : ConnectionRepository {
            override val events: SharedFlow<ConnectionEvent> = eventsFlow
            override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
            override suspend fun connect(url: String) {}
            override suspend fun disconnect() {}
            override fun sendText(message: String): Boolean = true
            override fun sendBinary(data: ByteArray): Boolean = true
        }

        val fakeAuthRepo = object : com.sentinel.admin.domain.repository.AuthRepository {
            override fun getToken(): String? = "test-token"
            override fun saveToken(token: String) {}
            override fun clearToken() {}
        }

        repo = DeviceRepositoryImpl(
            deviceApi = stubDeviceApi,
            authRepository = fakeAuthRepo,
            connectionRepository = fakeConnectionRepo,
            eventMapper = DeviceUpdateEventMapper(),
            moshi = moshi,
            scope = scope.backgroundScope
        )
    }

    /**
     * Emits a DEVICE_UPDATE message through the events flow.
     */
    private fun emitUpdate(data: DeviceUpdateDataJson, sequence: Long = 0) {
        val msg = DeviceUpdateMessageJson(
            type = "DEVICE_UPDATE",
            version = 1,
            timestamp = System.currentTimeMillis(),
            sequence = sequence,
            data = data
        )
        val json = messageAdapter.toJson(msg)
        eventsFlow.tryEmit(ConnectionEvent.DeviceUpdateReceived(json))
    }

    /**
     * Seeds a device into the map via a "connected" event.
     * Must call advanceUntilIdle() after to process.
     */
    private fun emitConnected(
        deviceId: String = "HOST-001",
        deviceName: String = "Test Device",
        sequence: Long = 0,
        model: String = "TestModel"
    ) {
        emitUpdate(
            DeviceUpdateDataJson(
                event = "connected",
                deviceId = deviceId,
                deviceName = deviceName,
                model = model,
                appVersion = "1.0"
            ),
            sequence = sequence
        )
    }

    /**
     * Seeds a device with a location by emitting connected + location events.
     * Must call advanceUntilIdle() after to process.
     */
    private fun emitConnectedWithLocation(
        deviceId: String = "HOST-001",
        deviceName: String = "Test Device",
        lat: Double = 37.0,
        lng: Double = -122.0,
        battery: Int = 80,
        network: String = "wifi",
        connectedSeq: Long = 1,
        locationSeq: Long = 2
    ) {
        emitConnected(deviceId, deviceName, connectedSeq)
        emitUpdate(
            DeviceUpdateDataJson(
                event = "location",
                deviceId = deviceId,
                latitude = lat,
                longitude = lng,
                accuracy = 5.0,
                battery = battery,
                network = network
            ),
            sequence = locationSeq
        )
    }

    // ============================================================
    // Connected events
    // ============================================================

    @Test
    fun `connected event creates device in map`() = scope.runTest {
        emitConnected(sequence = 1)
        advanceUntilIdle()

        val device = repo.devices.value["HOST-001"]
        assertNotNull(device)
        assertEquals("Test Device", device!!.deviceName)
        assertEquals("online", device.heartbeatStatus)
        assertEquals(1L, repo.eventStatistics.value.applied)
    }

    @Test
    fun `connected event patches existing offline device back online`() = scope.runTest {
        // Connect then disconnect
        emitConnected(sequence = 1)
        advanceUntilIdle()
        emitUpdate(
            DeviceUpdateDataJson(event = "disconnected", deviceId = "HOST-001"),
            sequence = 2
        )
        advanceUntilIdle()
        assertEquals("offline", repo.devices.value["HOST-001"]!!.heartbeatStatus)

        // Reconnect with updated name
        emitUpdate(
            DeviceUpdateDataJson(
                event = "connected",
                deviceId = "HOST-001",
                deviceName = "Updated Name"
            ),
            sequence = 3
        )
        advanceUntilIdle()

        val device = repo.devices.value["HOST-001"]
        assertNotNull(device)
        assertEquals("online", device!!.heartbeatStatus)
        assertEquals("Updated Name", device.deviceName)
    }

    // ============================================================
    // Heartbeat updates
    // ============================================================

    @Test
    fun `heartbeat update patches existing device`() = scope.runTest {
        emitConnected(sequence = 1)
        advanceUntilIdle()

        emitUpdate(
            DeviceUpdateDataJson(
                event = "heartbeat",
                deviceId = "HOST-001",
                timestamp = "2026-07-18T12:00:00Z"
            ),
            sequence = 2
        )
        advanceUntilIdle()

        val updated = repo.devices.value["HOST-001"]
        assertNotNull(updated)
        assertEquals("online", updated!!.heartbeatStatus)
        assertEquals("2026-07-18T12:00:00Z", updated.lastHeartbeat)
        assertEquals(2L, repo.eventStatistics.value.applied) // connected + heartbeat
    }

    @Test
    fun `heartbeat for unknown device is ignored`() = scope.runTest {
        emitUpdate(
            DeviceUpdateDataJson(
                event = "heartbeat",
                deviceId = "UNKNOWN",
                timestamp = "2026-07-18T12:00:00Z"
            ),
            sequence = 1
        )
        advanceUntilIdle()

        // Applied counter increments (event was valid), but no device in map
        assertTrue(repo.devices.value["UNKNOWN"] == null)
    }

    // ============================================================
    // Disconnect updates
    // ============================================================

    @Test
    fun `disconnect marks device offline not removed`() = scope.runTest {
        emitConnected(sequence = 1)
        advanceUntilIdle()

        emitUpdate(
            DeviceUpdateDataJson(event = "disconnected", deviceId = "HOST-001"),
            sequence = 2
        )
        advanceUntilIdle()

        val updated = repo.devices.value["HOST-001"]
        assertNotNull("Device should still be in map", updated)
        assertEquals("offline", updated!!.heartbeatStatus)
    }

    // ============================================================
    // Location updates
    // ============================================================

    @Test
    fun `location update patches coordinates`() = scope.runTest {
        emitConnectedWithLocation(connectedSeq = 1, locationSeq = 2)
        advanceUntilIdle()

        // Verify initial location
        val loc1 = repo.devices.value["HOST-001"]?.latestLocation
        assertNotNull(loc1)
        assertEquals(37.0, loc1!!.latitude, 0.001)

        // Update location
        emitUpdate(
            DeviceUpdateDataJson(
                event = "location",
                deviceId = "HOST-001",
                latitude = 38.0,
                longitude = -123.0,
                accuracy = 3.0,
                battery = 75,
                network = "5g"
            ),
            sequence = 3
        )
        advanceUntilIdle()

        val loc2 = repo.devices.value["HOST-001"]?.latestLocation
        assertNotNull(loc2)
        assertEquals(38.0, loc2!!.latitude, 0.001)
        assertEquals(-123.0, loc2.longitude, 0.001)
        assertEquals(75, loc2.battery)
        assertEquals("5g", loc2.network)
    }

    // ============================================================
    // Metadata updates
    // ============================================================

    @Test
    fun `metadata update patches device name`() = scope.runTest {
        emitConnected(sequence = 1)
        advanceUntilIdle()

        emitUpdate(
            DeviceUpdateDataJson(
                event = "metadata",
                deviceId = "HOST-001",
                deviceName = "Renamed Device"
            ),
            sequence = 2
        )
        advanceUntilIdle()

        assertEquals("Renamed Device", repo.devices.value["HOST-001"]?.deviceName)
    }

    // ============================================================
    // Duplicate suppression
    // ============================================================

    @Test
    fun `duplicate sequence is rejected`() = scope.runTest {
        emitConnected(sequence = 1)
        advanceUntilIdle()

        emitUpdate(
            DeviceUpdateDataJson(event = "heartbeat", deviceId = "HOST-001", timestamp = "t1"),
            sequence = 5
        )
        advanceUntilIdle()

        emitUpdate(
            DeviceUpdateDataJson(event = "heartbeat", deviceId = "HOST-001", timestamp = "t2"),
            sequence = 5
        )
        advanceUntilIdle()

        assertEquals(2L, repo.eventStatistics.value.applied) // connected + first heartbeat
        assertEquals(1L, repo.eventStatistics.value.duplicates)
    }

    // ============================================================
    // Stale rejection
    // ============================================================

    @Test
    fun `stale sequence is rejected`() = scope.runTest {
        emitConnected(sequence = 1)
        advanceUntilIdle()

        emitUpdate(
            DeviceUpdateDataJson(event = "heartbeat", deviceId = "HOST-001", timestamp = "t1"),
            sequence = 10
        )
        advanceUntilIdle()

        emitUpdate(
            DeviceUpdateDataJson(event = "heartbeat", deviceId = "HOST-001", timestamp = "t2"),
            sequence = 5
        )
        advanceUntilIdle()

        assertEquals(2L, repo.eventStatistics.value.applied) // connected + first heartbeat
        assertEquals(1L, repo.eventStatistics.value.stale)
    }

    // ============================================================
    // Unknown events
    // ============================================================

    @Test
    fun `unknown event type increments ignored counter`() = scope.runTest {
        emitConnected(sequence = 1)
        advanceUntilIdle()

        emitUpdate(
            DeviceUpdateDataJson(event = "future_unknown_event", deviceId = "HOST-001"),
            sequence = 2
        )
        advanceUntilIdle()

        assertEquals(1L, repo.eventStatistics.value.applied) // only connected
        assertEquals(1L, repo.eventStatistics.value.ignored) // unknown ignored
    }

    // ============================================================
    // Malformed payload
    // ============================================================

    @Test
    fun `malformed JSON increments ignored counter`() = scope.runTest {
        eventsFlow.tryEmit(ConnectionEvent.DeviceUpdateReceived("not valid json{{{"))
        advanceUntilIdle()

        assertEquals(0L, repo.eventStatistics.value.applied)
        assertEquals(1L, repo.eventStatistics.value.ignored)
    }

    // ============================================================
    // Sequence ordering across multiple devices
    // ============================================================

    @Test
    fun `different devices track sequences independently`() = scope.runTest {
        emitConnected("HOST-001", "Device 1", sequence = 1)
        emitConnected("HOST-002", "Device 2", sequence = 1)
        advanceUntilIdle()

        assertEquals(2L, repo.eventStatistics.value.applied)

        // HOST-001 at seq 10, HOST-002 at seq 5
        emitUpdate(
            DeviceUpdateDataJson(event = "heartbeat", deviceId = "HOST-001", timestamp = "t1"),
            sequence = 10
        )
        emitUpdate(
            DeviceUpdateDataJson(event = "heartbeat", deviceId = "HOST-002", timestamp = "t2"),
            sequence = 5
        )
        advanceUntilIdle()

        assertEquals(4L, repo.eventStatistics.value.applied) // 2 connected + 2 heartbeats

        // HOST-001 at seq 8 (stale) but HOST-002 at seq 8 (valid)
        emitUpdate(
            DeviceUpdateDataJson(event = "heartbeat", deviceId = "HOST-001", timestamp = "t3"),
            sequence = 8
        )
        emitUpdate(
            DeviceUpdateDataJson(event = "heartbeat", deviceId = "HOST-002", timestamp = "t4"),
            sequence = 8
        )
        advanceUntilIdle()

        assertEquals(5L, repo.eventStatistics.value.applied) // +1 HOST-002
        assertEquals(1L, repo.eventStatistics.value.stale)   // HOST-001's seq 8
    }

    @Test
    fun `dedicated telemetry report updates specific device and isolates from generic broadcast`() = scope.runTest {
        // Two devices with same deviceId but different models
        emitConnected("HOST-001", "Samsung S24", model = "SM-S928B", sequence = 1)
        emitConnected("HOST-001", "Vivo Phone", model = "I2401", sequence = 2)
        advanceUntilIdle()

        assertEquals(2, repo.devices.value.values.distinctBy { it.uniqueKey }.size)

        // Samsung sends dedicated TELEMETRY_REPORT with battery 45%
        val telemetryJson = """
            {
                "type": "COMMAND_RESULT",
                "version": 1,
                "timestamp": 12345,
                "sequence": 3,
                "data": {
                    "command": "TELEMETRY_REPORT",
                    "success": true,
                    "payload": {
                        "deviceId": "HOST-001",
                        "model": "SM-S928B",
                        "uniqueKey": "HOST-001_SM-S928B",
                        "battery": 45,
                        "network": "WiFi (Charging)",
                        "latitude": 28.3657,
                        "longitude": 77.5402,
                        "accuracy": 10.0
                    }
                }
            }
        """.trimIndent()
        eventsFlow.tryEmit(ConnectionEvent.CommandResultReceived(telemetryJson))
        advanceUntilIdle()

        val samsung = repo.devices.value["HOST-001_SM-S928B"]!!
        assertEquals(45, samsung.latestLocation?.battery)
        assertEquals("WiFi (Charging)", samsung.latestLocation?.network)

        // Server sends generic LOCATION broadcast for HOST-001 with battery 50% (from Vivo)
        emitUpdate(
            DeviceUpdateDataJson(
                event = "location",
                deviceId = "HOST-001",
                latitude = 28.3658,
                longitude = 77.5403,
                accuracy = 8.0,
                battery = 50,
                network = "WiFi"
            ),
            sequence = 4
        )
        advanceUntilIdle()

        // Samsung must still have 45% (isolated!)
        val samsungAfter = repo.devices.value["HOST-001_SM-S928B"]!!
        assertEquals(45, samsungAfter.latestLocation?.battery)
        assertEquals("WiFi (Charging)", samsungAfter.latestLocation?.network)

        // Vivo must have 50%
        val vivoAfter = repo.devices.value["HOST-001_I2401"]!!
        assertEquals(50, vivoAfter.latestLocation?.battery)
        assertEquals("WiFi", vivoAfter.latestLocation?.network)
    }

    @Test
    fun `dedicated telemetry echo from server is suppressed and does not overwrite other device`() = scope.runTest {
        // Two devices sharing HOST-001
        emitConnected("HOST-001", "Samsung S24", model = "SM-S928B", sequence = 1)
        emitConnected("HOST-001", "Vivo Phone", model = "I2401", sequence = 2)
        advanceUntilIdle()

        // Samsung has 45% battery
        val samsungTelemetry = """
            {
                "type": "COMMAND_RESULT",
                "version": 1,
                "timestamp": 12345,
                "sequence": 3,
                "data": {
                    "command": "GET_SYSTEM_INFO",
                    "success": true,
                    "payload": {
                        "deviceId": "HOST-001",
                        "model": "SM-S928B",
                        "uniqueKey": "HOST-001_SM-S928B",
                        "batteryPercent": 45,
                        "isCharging": false
                    }
                }
            }
        """.trimIndent()
        eventsFlow.tryEmit(ConnectionEvent.CommandResultReceived(samsungTelemetry))
        advanceUntilIdle()

        val samsungInitial = repo.devices.value["HOST-001_SM-S928B"]!!
        assertEquals(45, samsungInitial.latestLocation?.battery)

        // Vivo reports dedicated telemetry: 65% battery, charging, lat 28.3659, lng 77.5403
        val vivoTelemetry = """
            {
                "type": "COMMAND_RESULT",
                "version": 1,
                "timestamp": 12346,
                "sequence": 4,
                "data": {
                    "command": "TELEMETRY_REPORT",
                    "success": true,
                    "payload": {
                        "deviceId": "HOST-001",
                        "model": "I2401",
                        "uniqueKey": "HOST-001_I2401",
                        "battery": 65,
                        "network": "WiFi (Charging)",
                        "latitude": 28.3659,
                        "longitude": 77.5403,
                        "accuracy": 15.0
                    }
                }
            }
        """.trimIndent()
        eventsFlow.tryEmit(ConnectionEvent.CommandResultReceived(vivoTelemetry))
        advanceUntilIdle()

        val vivoCurrent = repo.devices.value["HOST-001_I2401"]!!
        assertEquals(65, vivoCurrent.latestLocation?.battery)

        // Server broadcasts untagged generic DEVICE_UPDATE echo of Vivo's report (deviceId: HOST-001, battery: 65)
        emitUpdate(
            DeviceUpdateDataJson(
                event = "location",
                deviceId = "HOST-001",
                latitude = 28.3659,
                longitude = 77.5403,
                accuracy = 15.0,
                battery = 65,
                network = "WiFi (Charging)"
            ),
            sequence = 5
        )
        advanceUntilIdle()

        // CRITICAL CHECK: Samsung must NOT be overwritten with Vivo's 65% echo!
        val samsungAfterEcho = repo.devices.value["HOST-001_SM-S928B"]!!
        assertEquals(45, samsungAfterEcho.latestLocation?.battery)

        // Vivo must still be 65%
        val vivoAfterEcho = repo.devices.value["HOST-001_I2401"]!!
        assertEquals(65, vivoAfterEcho.latestLocation?.battery)
    }
}
