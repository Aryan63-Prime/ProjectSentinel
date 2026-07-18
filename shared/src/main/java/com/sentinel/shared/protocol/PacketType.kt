package com.sentinel.shared.protocol

/**
 * Binary audio frame packet type per PROTOCOL.md.
 * Used as the first byte of every binary WebSocket frame.
 */
object PacketType {
    const val AUDIO: Byte = 0x01
}
