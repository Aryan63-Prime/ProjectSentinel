package com.sentinel.admin.domain.model

/**
 * Sealed state machine for audio playback.
 *
 * State transitions:
 * ```
 * Idle → Connecting → Buffering → Playing → Paused → Playing (resume)
 *                                         → Stopped → Idle
 * Any → Error
 * ```
 */
sealed interface PlaybackState {
    /** No active listening session. */
    data object Idle : PlaybackState

    /** LISTEN command sent, waiting for audio frames. */
    data object Connecting : PlaybackState

    /** Receiving frames, filling jitter buffer before playback. */
    data object Buffering : PlaybackState

    /** Actively decoding and playing audio. */
    data object Playing : PlaybackState

    /** Playback paused (e.g., WebSocket disconnected). */
    data object Paused : PlaybackState

    /** User stopped listening. */
    data object Stopped : PlaybackState

    /** Unrecoverable error. */
    data class Error(val message: String) : PlaybackState
}
