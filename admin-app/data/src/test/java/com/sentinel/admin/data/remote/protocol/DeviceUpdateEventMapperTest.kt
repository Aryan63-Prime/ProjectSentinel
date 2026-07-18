package com.sentinel.admin.data.remote.protocol

import com.sentinel.admin.domain.model.DeviceUpdateEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [DeviceUpdateEventMapper].
 *
 * Verifies mapping of all known event types and safe handling of unknown/malformed events.
 */
class DeviceUpdateEventMapperTest {

    private lateinit var mapper: DeviceUpdateEventMapper

    @Before
    fun setUp() {
        mapper = DeviceUpdateEventMapper()
    }

    @Test
    fun `connected event maps correctly`() {
        val data = DeviceUpdateDataJson(
            event = "connected",
            deviceId = "HOST-001",
            deviceName = "Pixel 9",
            appVersion = "2.1.0",
            model = "Pixel 9 Pro"
        )
        val result = mapper.map(data)
        assertTrue(result is DeviceUpdateEvent.DeviceConnected)
        val connected = result as DeviceUpdateEvent.DeviceConnected
        assertEquals("HOST-001", connected.deviceId)
        assertEquals("Pixel 9", connected.deviceName)
        assertEquals("2.1.0", connected.appVersion)
        assertEquals("Pixel 9 Pro", connected.model)
    }

    @Test
    fun `disconnected event maps correctly`() {
        val data = DeviceUpdateDataJson(event = "disconnected", deviceId = "HOST-001")
        val result = mapper.map(data)
        assertTrue(result is DeviceUpdateEvent.DeviceDisconnected)
        assertEquals("HOST-001", result!!.deviceId)
    }

    @Test
    fun `heartbeat event maps correctly`() {
        val data = DeviceUpdateDataJson(
            event = "heartbeat",
            deviceId = "HOST-001",
            timestamp = "2026-07-18T04:00:00Z"
        )
        val result = mapper.map(data)
        assertTrue(result is DeviceUpdateEvent.HeartbeatReceived)
        val hb = result as DeviceUpdateEvent.HeartbeatReceived
        assertEquals("2026-07-18T04:00:00Z", hb.timestamp)
    }

    @Test
    fun `location event maps correctly`() {
        val data = DeviceUpdateDataJson(
            event = "location",
            deviceId = "HOST-001",
            latitude = 37.7749,
            longitude = -122.4194,
            accuracy = 10.0,
            battery = 85,
            network = "wifi"
        )
        val result = mapper.map(data)
        assertTrue(result is DeviceUpdateEvent.LocationUpdated)
        val loc = result as DeviceUpdateEvent.LocationUpdated
        assertEquals(37.7749, loc.latitude, 0.0001)
        assertEquals(-122.4194, loc.longitude, 0.0001)
        assertEquals(85, loc.battery)
        assertEquals("wifi", loc.network)
    }

    @Test
    fun `location event without coordinates returns null`() {
        val data = DeviceUpdateDataJson(
            event = "location",
            deviceId = "HOST-001",
            battery = 85
        )
        val result = mapper.map(data)
        assertNull(result)
    }

    @Test
    fun `battery event maps correctly`() {
        val data = DeviceUpdateDataJson(
            event = "battery",
            deviceId = "HOST-001",
            battery = 42
        )
        val result = mapper.map(data)
        assertTrue(result is DeviceUpdateEvent.BatteryUpdated)
        assertEquals(42, (result as DeviceUpdateEvent.BatteryUpdated).battery)
    }

    @Test
    fun `network event maps correctly`() {
        val data = DeviceUpdateDataJson(
            event = "network",
            deviceId = "HOST-001",
            network = "5g"
        )
        val result = mapper.map(data)
        assertTrue(result is DeviceUpdateEvent.NetworkUpdated)
        assertEquals("5g", (result as DeviceUpdateEvent.NetworkUpdated).network)
    }

    @Test
    fun `metadata event maps correctly`() {
        val data = DeviceUpdateDataJson(
            event = "metadata",
            deviceId = "HOST-001",
            deviceName = "New Name"
        )
        val result = mapper.map(data)
        assertTrue(result is DeviceUpdateEvent.MetadataUpdated)
        assertEquals("New Name", (result as DeviceUpdateEvent.MetadataUpdated).deviceName)
    }

    @Test
    fun `unknown event type returns null`() {
        val data = DeviceUpdateDataJson(event = "future_event", deviceId = "HOST-001")
        val result = mapper.map(data)
        assertNull(result)
    }

    @Test
    fun `empty deviceId returns null`() {
        val data = DeviceUpdateDataJson(event = "heartbeat", deviceId = "")
        val result = mapper.map(data)
        assertNull(result)
    }

    @Test
    fun `blank deviceId returns null`() {
        val data = DeviceUpdateDataJson(event = "heartbeat", deviceId = "   ")
        val result = mapper.map(data)
        assertNull(result)
    }

    @Test
    fun `battery event without value returns null`() {
        val data = DeviceUpdateDataJson(event = "battery", deviceId = "HOST-001")
        val result = mapper.map(data)
        assertNull(result)
    }

    @Test
    fun `network event without value returns null`() {
        val data = DeviceUpdateDataJson(event = "network", deviceId = "HOST-001")
        val result = mapper.map(data)
        assertNull(result)
    }
}
