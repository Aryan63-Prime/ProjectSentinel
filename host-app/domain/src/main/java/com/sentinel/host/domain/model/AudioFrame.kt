package com.sentinel.host.domain.model

/**
 * A single encoded audio frame ready for binary WebSocket transmission.
 * Layout: [1B packetType][4B sequence][8B timestamp][opusData]
 */
data class AudioFrame(
    val packetType: Byte,
    val sequence: Long,
    val timestamp: Long,
    val opusData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return packetType == other.packetType &&
            sequence == other.sequence &&
            timestamp == other.timestamp &&
            opusData.contentEquals(other.opusData)
    }

    override fun hashCode(): Int {
        var result = packetType.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + opusData.contentHashCode()
        return result
    }
}
