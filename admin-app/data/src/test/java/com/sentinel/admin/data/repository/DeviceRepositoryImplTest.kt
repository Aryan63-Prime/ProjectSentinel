package com.sentinel.admin.data.repository

import com.sentinel.admin.data.remote.api.DeviceApi
import com.sentinel.admin.data.remote.protocol.DeviceUpdateEventMapper
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.ConnectionState
import com.sentinel.admin.domain.repository.AuthRepository
import com.sentinel.admin.domain.repository.ConnectionRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Unit tests for [DeviceRepositoryImpl].
 *
 * Uses MockWebServer + Retrofit to test:
 * - Successful device list fetch
 * - Successful single device fetch
 * - HTTP error mapping (401, 404, 500)
 * - Network error handling
 * - DTO → domain mapping
 * - Missing auth token
 * - Empty device list
 * - Nullable location
 */
class DeviceRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DeviceApi
    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var repo: DeviceRepositoryImpl
    private lateinit var moshi: Moshi

    private class FakeAuthRepository : AuthRepository {
        private var storedToken: String? = "test-jwt-token"
        override fun getToken(): String? = storedToken
        override fun saveToken(token: String) { storedToken = token }
        override fun clearToken() { storedToken = null }
        fun setToken(value: String?) { storedToken = value }
    }

    private val noopConnectionRepo = object : ConnectionRepository {
        override val events: SharedFlow<ConnectionEvent> = MutableSharedFlow()
        override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
        override suspend fun connect(url: String) {}
        override suspend fun disconnect() {}
        override fun sendText(message: String): Boolean = true
        override fun sendBinary(data: ByteArray): Boolean = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        api = retrofit.create(DeviceApi::class.java)
        fakeAuthRepo = FakeAuthRepository()
        repo = DeviceRepositoryImpl(
            api, fakeAuthRepo, noopConnectionRepo,
            DeviceUpdateEventMapper(), moshi,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ============================================================
    // GET /devices — Success
    // ============================================================

    @Test
    fun `getDevices success with location`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(DEVICES_RESPONSE_WITH_LOCATION)
        )

        val result = repo.getDevices()
        assertTrue(result.isSuccess)

        val devices = result.getOrThrow()
        assertEquals(1, devices.size)

        val device = devices[0]
        assertEquals("HOST-0001", device.deviceId)
        assertEquals("Pixel 9", device.deviceName)
        assertEquals("Google Pixel", device.model)
        assertEquals("online", device.heartbeatStatus)
        assertEquals(true, device.authenticated)
        assertEquals(true, device.registered)
        assertEquals("registered", device.registrationState)

        // Location mapped
        assertNotNull(device.latestLocation)
        assertEquals(28.6139, device.latestLocation!!.latitude, 0.0001)
        assertEquals(81, device.latestLocation!!.battery)
        assertEquals("5G", device.latestLocation!!.network)

        // Verify auth header
        val request = server.takeRequest()
        assertEquals("Bearer test-jwt-token", request.getHeader("Authorization"))
    }

    @Test
    fun `getDevices success without location`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(DEVICES_RESPONSE_NO_LOCATION)
        )

        val result = repo.getDevices()
        assertTrue(result.isSuccess)

        val device = result.getOrThrow()[0]
        assertNull(device.latestLocation)
    }

    @Test
    fun `getDevices empty list`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"devices":[]}""")
        )

        val result = repo.getDevices()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `getDevices multiple devices`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(DEVICES_RESPONSE_MULTIPLE)
        )

        val result = repo.getDevices()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    // ============================================================
    // GET /devices/{deviceId} — Success
    // ============================================================

    @Test
    fun `getDevice success`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(SINGLE_DEVICE_RESPONSE)
        )

        val result = repo.getDevice("HOST-0001")
        assertTrue(result.isSuccess)
        assertEquals("HOST-0001", result.getOrThrow().deviceId)
    }

    // ============================================================
    // HTTP Errors
    // ============================================================

    @Test
    fun `getDevices 401 unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repo.getDevices()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unauthorized") == true)
    }

    @Test
    fun `getDevice 404 not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = repo.getDevice("INVALID")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `getDevices 500 server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repo.getDevices()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Server error") == true)
    }

    @Test
    fun `getDevices 403 forbidden`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = repo.getDevices()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Forbidden") == true)
    }

    // ============================================================
    // Auth edge cases
    // ============================================================

    @Test
    fun `getDevices without token fails`() = runTest {
        fakeAuthRepo.setToken(null)

        val result = repo.getDevices()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Not authenticated") == true)
    }

    @Test
    fun `getDevice without token fails`() = runTest {
        fakeAuthRepo.setToken(null)

        val result = repo.getDevice("HOST-0001")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Not authenticated") == true)
    }

    // ============================================================
    // Mapping
    // ============================================================

    @Test
    fun `location fields map correctly`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(DEVICES_RESPONSE_WITH_LOCATION)
        )

        val loc = repo.getDevices().getOrThrow()[0].latestLocation!!
        assertEquals("HOST-0001", loc.deviceId)
        assertEquals(28.6139, loc.latitude, 0.0001)
        assertEquals(77.2090, loc.longitude, 0.0001)
        assertEquals(5.4, loc.accuracy, 0.01)
        assertEquals(81, loc.battery)
        assertEquals("5G", loc.network)
        assertEquals("2026-07-09T12:00:10Z", loc.recordedAt)
    }

    // ============================================================
    // Test data
    // ============================================================

    companion object {
        private val DEVICES_RESPONSE_WITH_LOCATION = """
        {
          "devices": [{
            "deviceId": "HOST-0001",
            "connectionId": "CONN-001",
            "authenticated": true,
            "registered": true,
            "registrationState": "registered",
            "heartbeatStatus": "online",
            "connectedAt": "2026-07-09T12:00:00Z",
            "lastHeartbeat": "2026-07-09T12:00:20Z",
            "deviceName": "Pixel 9",
            "appVersion": "1.0.0",
            "model": "Google Pixel",
            "latestLocation": {
              "deviceId": "HOST-0001",
              "latitude": 28.6139,
              "longitude": 77.2090,
              "accuracy": 5.4,
              "battery": 81,
              "network": "5G",
              "recordedAt": "2026-07-09T12:00:10Z"
            }
          }]
        }
        """.trimIndent()

        private val DEVICES_RESPONSE_NO_LOCATION = """
        {
          "devices": [{
            "deviceId": "HOST-0002",
            "connectionId": "CONN-002",
            "authenticated": true,
            "registered": true,
            "registrationState": "registered",
            "heartbeatStatus": "offline",
            "connectedAt": "2026-07-09T11:00:00Z",
            "lastHeartbeat": "2026-07-09T11:05:00Z",
            "deviceName": "Samsung S24",
            "appVersion": "1.0.0",
            "model": "Samsung",
            "latestLocation": null
          }]
        }
        """.trimIndent()

        private val DEVICES_RESPONSE_MULTIPLE = """
        {
          "devices": [
            {
              "deviceId": "HOST-0001",
              "connectionId": "CONN-001",
              "authenticated": true,
              "registered": true,
              "registrationState": "registered",
              "heartbeatStatus": "online",
              "connectedAt": "2026-07-09T12:00:00Z",
              "lastHeartbeat": "2026-07-09T12:00:20Z",
              "deviceName": "Pixel 9",
              "appVersion": "1.0.0",
              "model": "Google Pixel",
              "latestLocation": null
            },
            {
              "deviceId": "HOST-0002",
              "connectionId": "CONN-002",
              "authenticated": true,
              "registered": false,
              "registrationState": "pending",
              "heartbeatStatus": "offline",
              "connectedAt": "2026-07-09T11:00:00Z",
              "lastHeartbeat": "2026-07-09T11:05:00Z",
              "deviceName": "OnePlus 12",
              "appVersion": "1.0.0",
              "model": "OnePlus",
              "latestLocation": null
            }
          ]
        }
        """.trimIndent()

        private val SINGLE_DEVICE_RESPONSE = """
        {
          "deviceId": "HOST-0001",
          "connectionId": "CONN-001",
          "authenticated": true,
          "registered": true,
          "registrationState": "registered",
          "heartbeatStatus": "online",
          "connectedAt": "2026-07-09T12:00:00Z",
          "lastHeartbeat": "2026-07-09T12:00:20Z",
          "deviceName": "Pixel 9",
          "appVersion": "1.0.0",
          "model": "Google Pixel",
          "latestLocation": null
        }
        """.trimIndent()
    }
}
