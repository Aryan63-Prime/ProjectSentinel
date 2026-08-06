package com.sentinel.host.domain.motion

import com.sentinel.host.domain.model.MotionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for hardware-assisted physical motion detection.
 * Operates in-memory with zero disk footprint.
 */
interface MotionDetector {
    /** Current physical motion state of the device. */
    val motionState: StateFlow<MotionState>

    /** Starts sensor sampling and hardware triggers. */
    fun start()

    /** Stops sensor sampling to conserve battery when idle. */
    fun stop()
}
