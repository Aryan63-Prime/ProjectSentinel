package com.sentinel.admin.data.audio

import com.sentinel.shared.protocol.AudioConstants
import com.sentinel.shared.protocol.PacketType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses binary audio frames per PROTOCOL.md.
 *
 * Frame layout:
 * [1B PacketType][4B Sequence (big-endian uint32)][8B Timestamp (big-endian int64)][...Opus payload]
 *
 * Total header: 13 bytes.
 */
object AudioFrameParser {

    /**
     * Parses a binary frame into an [AudioFrame].
     * Returns null if validation fails — never throws.
     *
     * Validation:
     * - Frame size >= HEADER_SIZE (13)
     * - PacketType == AUDIO (0x01)
     * - Payload length > 0
     */
    fun parse(data: ByteArray): AudioFrame? {
        if (data.size < AudioConstants.HEADER_SIZE) return null

        val packetType = data[0]
        if (packetType != PacketType.AUDIO) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Read sequence as unsigned 32-bit → stored as Long
        val sequence = buffer.getInt(1).toLong() and 0xFFFFFFFFL

        val timestamp = buffer.getLong(5)

        val payloadSize = data.size - AudioConstants.HEADER_SIZE
        if (payloadSize <= 0) return null

        val payload = ByteArray(payloadSize)
        System.arraycopy(data, AudioConstants.HEADER_SIZE, payload, 0, payloadSize)

        return AudioFrame(
            packetType = packetType,
            sequence = sequence,
            timestamp = timestamp,
            payload = payload
        )
    }
}

/**
 * Parsed audio frame with validated header.
 *
 * @property sequence UInt32 stored as Long (0..4294967295).
 */
data class AudioFrame(
    val packetType: Byte,
    val sequence: Long,
    val timestamp: Long,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return sequence == other.sequence &&
            timestamp == other.timestamp &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
