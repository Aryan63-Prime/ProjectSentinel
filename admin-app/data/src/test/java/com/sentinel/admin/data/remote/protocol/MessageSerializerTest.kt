package com.sentinel.admin.data.remote.protocol

import com.sentinel.shared.protocol.MessageType
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MessageSerializer].
 *
 * Covers:
 * - AUTH serialization (envelope structure, token, sequence)
 * - HEARTBEAT serialization (empty data)
 * - LISTEN/STOP serialization (deviceId in data)
 * - AUTH_ACK deserialization (success/failure)
 * - HEARTBEAT_ACK deserialization
 * - ERROR deserialization (code + message)
 * - Unknown message type deserialization
 * - Malformed JSON handling
 */
class MessageSerializerTest {

    private lateinit var serializer: MessageSerializer

    @Before
    fun setUp() {
        serializer = MessageSerializer(Moshi.Builder().build())
    }

    // ============================================================
    // AUTH serialization
    // ============================================================

    @Test
    fun `serializeAuth produces valid envelope with token`() {
        val json = serializer.serializeAuth("test-jwt-token", 1)

        assertTrue("Must contain AUTH type", json.contains("\"type\":\"AUTH\""))
        assertTrue("Must contain token", json.contains("\"token\":\"test-jwt-token\""))
        assertTrue("Must contain sequence", json.contains("\"sequence\":1"))
        assertTrue("Must contain version", json.contains("\"version\":"))
        assertTrue("Must contain timestamp", json.contains("\"timestamp\":"))
    }

    @Test
    fun `serializeAuth preserves sequence number`() {
        val json1 = serializer.serializeAuth("token", 42)
        val json2 = serializer.serializeAuth("token", 99)

        assertTrue(json1.contains("\"sequence\":42"))
        assertTrue(json2.contains("\"sequence\":99"))
    }

    // ============================================================
    // HEARTBEAT serialization
    // ============================================================

    @Test
    fun `serializeHeartbeat produces valid envelope with empty data`() {
        val json = serializer.serializeHeartbeat(5)

        assertTrue(json.contains("\"type\":\"HEARTBEAT\""))
        assertTrue(json.contains("\"sequence\":5"))
        assertTrue("Data should be empty object", json.contains("\"data\":{}"))
    }

    // ============================================================
    // LISTEN serialization
    // ============================================================

    @Test
    fun `serializeListen produces valid envelope with deviceId`() {
        val json = serializer.serializeListen("HOST-0001", 10)

        assertTrue(json.contains("\"type\":\"LISTEN\""))
        assertTrue(json.contains("\"deviceId\":\"HOST-0001\""))
        assertTrue(json.contains("\"sequence\":10"))
    }

    // ============================================================
    // STOP serialization
    // ============================================================

    @Test
    fun `serializeStop produces valid envelope with deviceId`() {
        val json = serializer.serializeStop("HOST-0002", 15)

        assertTrue(json.contains("\"type\":\"STOP\""))
        assertTrue(json.contains("\"deviceId\":\"HOST-0002\""))
        assertTrue(json.contains("\"sequence\":15"))
    }

    // ============================================================
    // AUTH_ACK deserialization
    // ============================================================

    @Test
    fun `deserialize AUTH_ACK success`() {
        val json = """{"type":"AUTH_ACK","version":1,"timestamp":1234,"sequence":1,"data":{"success":true}}"""
        val msg = serializer.deserialize(json)

        assertTrue(msg is IncomingMessage.AuthAck)
        val authAck = msg as IncomingMessage.AuthAck
        assertTrue(authAck.success)
        assertEquals(MessageType.AUTH_ACK, authAck.type)
        assertEquals(1L, authAck.sequence)
    }

    @Test
    fun `deserialize AUTH_ACK failure`() {
        val json = """{"type":"AUTH_ACK","version":1,"timestamp":1234,"sequence":2,"data":{"success":false}}"""
        val msg = serializer.deserialize(json)

        assertTrue(msg is IncomingMessage.AuthAck)
        assertFalse((msg as IncomingMessage.AuthAck).success)
    }

    // ============================================================
    // HEARTBEAT_ACK deserialization
    // ============================================================

    @Test
    fun `deserialize HEARTBEAT_ACK`() {
        val json = """{"type":"HEARTBEAT_ACK","version":1,"timestamp":1234,"sequence":5}"""
        val msg = serializer.deserialize(json)

        assertTrue(msg is IncomingMessage.HeartbeatAck)
        assertEquals(5L, (msg as IncomingMessage.HeartbeatAck).sequence)
    }

    // ============================================================
    // ERROR deserialization
    // ============================================================

    @Test
    fun `deserialize ERROR with code and message`() {
        val json = """{"type":"ERROR","version":1,"timestamp":1234,"sequence":3,"data":{"code":401,"message":"Unauthorized"}}"""
        val msg = serializer.deserialize(json)

        assertTrue(msg is IncomingMessage.Error)
        val error = msg as IncomingMessage.Error
        assertEquals(401, error.code)
        assertEquals("Unauthorized", error.message)
        assertEquals(3L, error.sequence)
    }

    // ============================================================
    // PONG deserialization
    // ============================================================

    @Test
    fun `deserialize PONG`() {
        val json = """{"type":"PONG","version":1,"timestamp":1234,"sequence":7}"""
        val msg = serializer.deserialize(json)

        assertTrue(msg is IncomingMessage.Pong)
        assertEquals(7L, (msg as IncomingMessage.Pong).sequence)
    }

    // ============================================================
    // Unknown type
    // ============================================================

    @Test
    fun `deserialize unknown type returns Unknown`() {
        val json = """{"type":"FOOBAR","version":1,"timestamp":1234,"sequence":9}"""
        val msg = serializer.deserialize(json)

        assertTrue(msg is IncomingMessage.Unknown)
        assertEquals("FOOBAR", msg.type)
    }

    // ============================================================
    // Malformed JSON
    // ============================================================

    @Test
    fun `deserialize malformed JSON returns Unknown`() {
        val msg = serializer.deserialize("{invalid json")
        assertTrue(msg is IncomingMessage.Unknown)
    }

    @Test
    fun `deserialize empty string returns Unknown`() {
        val msg = serializer.deserialize("")
        assertTrue(msg is IncomingMessage.Unknown)
    }

    // ============================================================
    // Round-trip
    // ============================================================

    @Test
    fun `serialize and deserialize AUTH preserves structure`() {
        val serialized = serializer.serializeAuth("round-trip-token", 42)
        // The serialized message has type AUTH, which the deserializer treats as Unknown
        // (since the server never sends back raw AUTH). This is correct behavior.
        val deserialized = serializer.deserialize(serialized)
        assertTrue(deserialized is IncomingMessage.Unknown)
        assertEquals(MessageType.AUTH, deserialized.type)
        assertEquals(42L, deserialized.sequence)
    }

    // ============================================================
    // DEVICE_UPDATE deserialization
    // ============================================================

    @Test
    fun `deserialize DEVICE_UPDATE heartbeat`() {
        val json = """
            {
                "type": "DEVICE_UPDATE",
                "version": 1,
                "timestamp": 1720000000000,
                "sequence": 100,
                "data": {
                    "event": "heartbeat",
                    "deviceId": "HOST-001",
                    "timestamp": "2026-07-18T04:00:00Z"
                }
            }
        """.trimIndent()

        val result = serializer.deserialize(json)
        assertTrue(result is IncomingMessage.DeviceUpdate)
        val update = result as IncomingMessage.DeviceUpdate
        assertEquals(MessageType.DEVICE_UPDATE, update.type)
        assertEquals(100L, update.sequence)
        assertEquals("heartbeat", update.updateData.event)
        assertEquals("HOST-001", update.updateData.deviceId)
        assertEquals("2026-07-18T04:00:00Z", update.updateData.timestamp)
    }

    @Test
    fun `deserialize DEVICE_UPDATE location with all fields`() {
        val json = """
            {
                "type": "DEVICE_UPDATE",
                "version": 1,
                "timestamp": 1720000000000,
                "sequence": 200,
                "data": {
                    "event": "location",
                    "deviceId": "HOST-001",
                    "latitude": 37.7749,
                    "longitude": -122.4194,
                    "accuracy": 10.5,
                    "battery": 85,
                    "network": "wifi"
                }
            }
        """.trimIndent()

        val result = serializer.deserialize(json)
        assertTrue(result is IncomingMessage.DeviceUpdate)
        val update = result as IncomingMessage.DeviceUpdate
        assertEquals("location", update.updateData.event)
        assertEquals(37.7749, update.updateData.latitude!!, 0.0001)
        assertEquals(-122.4194, update.updateData.longitude!!, 0.0001)
        assertEquals(85, update.updateData.battery)
        assertEquals("wifi", update.updateData.network)
    }

    @Test
    fun `deserialize DEVICE_UPDATE with unknown event still parses`() {
        val json = """
            {
                "type": "DEVICE_UPDATE",
                "version": 1,
                "timestamp": 1720000000000,
                "sequence": 300,
                "data": {
                    "event": "future_event",
                    "deviceId": "HOST-001"
                }
            }
        """.trimIndent()

        val result = serializer.deserialize(json)
        assertTrue(result is IncomingMessage.DeviceUpdate)
        val update = result as IncomingMessage.DeviceUpdate
        assertEquals("future_event", update.updateData.event)
    }

    @Test
    fun `deserialize DEVICE_UPDATE with malformed data returns unknown`() {
        val json = """
            {
                "type": "DEVICE_UPDATE",
                "version": 1,
                "timestamp": 1720000000000,
                "sequence": 400,
                "data": "not_an_object"
            }
        """.trimIndent()

        val result = serializer.deserialize(json)
        assertTrue(result is IncomingMessage.Unknown)
    }
}
