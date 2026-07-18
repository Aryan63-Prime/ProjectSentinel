package com.sentinel.host.domain.repository

import com.sentinel.host.domain.model.AudioFrame
import kotlinx.coroutines.flow.Flow

/**
 * Manages audio capture, Opus encoding, and binary frame streaming.
 */
interface AudioRepository {
    fun startCapture(): Flow<AudioFrame>
    fun stopCapture()
}
