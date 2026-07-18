package com.sentinel.host.data.audio

import com.sentinel.host.domain.model.AudioFrame
import com.sentinel.shared.protocol.AudioConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serializes [AudioFrame] into a binary packet for WebSocket transmission.
 *
 * Binary layout (per PROTOCOL.md / AudioConstants):
 * ```
 * [0]      PacketType    1 byte   (0x01 = AUDIO)
 * [1..4]   Sequence      4 bytes  (Big-Endian uint32)
 * [5..12]  Timestamp     8 bytes  (Big-Endian int64, epoch millis)
 * [13..]   OpusData      N bytes  (variable, Opus encoded audio)
 * ```
 * Total: HEADER_SIZE (13) + opus payload length.
 *
 * Buffer ownership:
 * - Reuses a pre-allocated [packetBuffer] to avoid allocations in the hot path.
 * - Returns a **copy** of the used portion — caller owns the returned array.
 * - The internal buffer is sized for max possible frame (HEADER + MAX_OPUS).
 *
 * Thread safety:
 * - NOT thread-safe. Must be used from a single thread (audio dispatcher).
 */
class AudioFrameBuilder {

    companion object {
        /** Maximum Opus encoded frame size in bytes. */
        private const val MAX_OPUS_FRAME_SIZE = 4000

        /** Maximum total packet size = header + max Opus frame. */
        private const val MAX_PACKET_SIZE = AudioConstants.HEADER_SIZE + MAX_OPUS_FRAME_SIZE
    }

    /**
     * Pre-allocated packet buffer — reused every frame.
     * Sized for the worst case: 13-byte header + 4000-byte Opus frame.
     */
    private val packetBuffer = ByteBuffer.allocate(MAX_PACKET_SIZE).apply {
        order(ByteOrder.BIG_ENDIAN)
    }

    /**
     * Serializes an [AudioFrame] into a binary packet.
     *
     * @param frame The audio frame to serialize.
     * @return A new ByteArray containing the binary packet, or null if the frame is invalid.
     */
    fun build(frame: AudioFrame): ByteArray? {
        if (frame.opusData.isEmpty()) return null

        val totalSize = AudioConstants.HEADER_SIZE + frame.opusData.size
        if (totalSize > MAX_PACKET_SIZE) return null

        packetBuffer.clear()

        // 1B packet type
        packetBuffer.put(frame.packetType)

        // 4B sequence (big-endian)
        packetBuffer.putInt(frame.sequence.toInt())

        // 8B timestamp (big-endian)
        packetBuffer.putLong(frame.timestamp)

        // Opus payload
        packetBuffer.put(frame.opusData)

        // Extract the used portion as a new array
        val result = ByteArray(totalSize)
        packetBuffer.flip()
        packetBuffer.get(result)
        return result
    }
}
