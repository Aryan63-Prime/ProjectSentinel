package com.sentinel.host.domain.model

/**
 * Physical motion state of the device derived from low-power hardware sensors.
 */
enum class MotionState {
    /** Device is at rest (desktop, nightstand, parked vehicle). Low-power GPS sampling. */
    STATIONARY,

    /** Device is physically moving (walking, driving). High-precision GPS sampling. */
    IN_MOTION
}
