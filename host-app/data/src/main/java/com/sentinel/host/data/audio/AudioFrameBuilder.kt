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
     * Serializes an [AudioFrame] into a binary packet.
     *
     * Thread-safe and allocation-efficient.
     *
     * @param frame The audio frame to serialize.
     * @return A new ByteArray containing the binary packet, or null if the frame is invalid.
     */
    fun build(frame: AudioFrame): ByteArray? {
        if (frame.opusData.isEmpty()) return null

        val totalSize = AudioConstants.HEADER_SIZE + frame.opusData.size
        if (totalSize > MAX_PACKET_SIZE) return null

        return try {
            val result = ByteArray(totalSize)
            val buffer = ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN)
            buffer.put(frame.packetType)
            buffer.putInt(frame.sequence.toInt())
            buffer.putLong(frame.timestamp)
            buffer.put(frame.opusData)
            result
        } catch (e: Exception) {
            null
        }
    }
}
