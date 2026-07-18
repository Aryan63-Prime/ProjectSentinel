package com.sentinel.host.data.remote.protocol

import com.sentinel.shared.protocol.MessageType
import com.sentinel.shared.protocol.ProtocolVersion
import com.squareup.moshi.Moshi
import okio.Buffer

/**
 * Serializes outgoing control messages and deserializes incoming ones.
 *
 * Outgoing: builds the protocol envelope with JsonWriter, delegates
 * the "data" field to typed Moshi adapters.
 *
 * Incoming: extracts "type" from the envelope, then re-parses the
 * full message with the correct typed adapter.
 */
class MessageSerializer(private val moshi: Moshi = Moshi.Builder().build()) {

    // ---- Adapters (lazy, thread-safe) ----

    private val envelopeAdapter by lazy { moshi.adapter(EnvelopeJson::class.java) }
    private val authDataAdapter by lazy { moshi.adapter(AuthDataJson::class.java) }
    private val registerDataAdapter by lazy { moshi.adapter(RegisterDataJson::class.java) }
    private val locationDataAdapter by lazy { moshi.adapter(LocationDataJson::class.java) }
    private val ackMessageAdapter by lazy { moshi.adapter(AckMessageJson::class.java) }
    private val errorMessageAdapter by lazy { moshi.adapter(ErrorMessageJson::class.java) }

    // ============================================================
    // Outgoing serialization
    // ============================================================

    fun serializeAuth(token: String, sequence: Long): String {
        return buildEnvelope(MessageType.AUTH, sequence) { writer ->
            authDataAdapter.toJson(writer, AuthDataJson(token))
        }
    }

    fun serializeRegister(
        deviceId: String,
        deviceName: String,
        appVersion: String,
        model: String,
        sequence: Long
    ): String {
        val data = RegisterDataJson(deviceId, deviceName, appVersion, model)
        return buildEnvelope(MessageType.REGISTER, sequence) { writer ->
            registerDataAdapter.toJson(writer, data)
        }
    }

    fun serializeHeartbeat(sequence: Long): String {
        return buildEnvelope(MessageType.HEARTBEAT, sequence) { writer ->
            writer.beginObject()
            writer.endObject()
        }
    }

    fun serializeLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        battery: Int,
        network: String,
        sequence: Long
    ): String {
        val data = LocationDataJson(latitude, longitude, accuracy, battery, network)
        return buildEnvelope(MessageType.LOCATION, sequence) { writer ->
            locationDataAdapter.toJson(writer, data)
        }
    }

    // ============================================================
    // Incoming deserialization
    // ============================================================

    fun deserialize(json: String): IncomingMessage {
        val envelope = envelopeAdapter.fromJson(json)
            ?: return IncomingMessage.Unknown("", 0)

        return when (envelope.type) {
            MessageType.AUTH_ACK -> {
                val msg = ackMessageAdapter.fromJson(json)
                IncomingMessage.AuthAck(
                    type = envelope.type,
                    sequence = envelope.sequence,
                    success = msg?.data?.success ?: false
                )
            }

            MessageType.REGISTER_ACK -> {
                val msg = ackMessageAdapter.fromJson(json)
                IncomingMessage.RegisterAck(
                    type = envelope.type,
                    sequence = envelope.sequence,
                    success = msg?.data?.success ?: false
                )
            }

            MessageType.HEARTBEAT_ACK -> {
                IncomingMessage.HeartbeatAck(
                    type = envelope.type,
                    sequence = envelope.sequence
                )
            }

            MessageType.PONG -> {
                IncomingMessage.Pong(
                    type = envelope.type,
                    sequence = envelope.sequence
                )
            }

            MessageType.ERROR -> {
                val msg = errorMessageAdapter.fromJson(json)
                IncomingMessage.Error(
                    type = envelope.type,
                    sequence = envelope.sequence,
                    code = msg?.data?.code ?: 0,
                    message = msg?.data?.message ?: ""
                )
            }

            else -> IncomingMessage.Unknown(
                type = envelope.type,
                sequence = envelope.sequence
            )
        }
    }

    // ============================================================
    // Internal
    // ============================================================

    /**
     * Builds the standard protocol envelope JSON.
     * The [writeData] lambda writes the "data" field content.
     */
    private fun buildEnvelope(
        type: String,
        sequence: Long,
        writeData: (com.squareup.moshi.JsonWriter) -> Unit
    ): String {
        val buffer = Buffer()
        val writer = com.squareup.moshi.JsonWriter.of(buffer)
        writer.beginObject()
        writer.name("type").value(type)
        writer.name("version").value(ProtocolVersion.CURRENT.toLong())
        writer.name("timestamp").value(System.currentTimeMillis())
        writer.name("sequence").value(sequence)
        writer.name("data")
        writeData(writer)
        writer.endObject()
        writer.close()
        return buffer.readUtf8()
    }
}
