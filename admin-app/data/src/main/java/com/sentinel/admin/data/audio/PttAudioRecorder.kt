package com.sentinel.admin.data.audio

/**
 * Abstraction for recording voice microphone input in Push-To-Talk (PTT) intercom mode.
 */
interface PttAudioRecorder {
    /** Whether microphone audio is currently being captured. */
    val isRecording: Boolean

    /**
     * Starts capturing microphone audio at 16 kHz Mono 16-bit PCM.
     * Invokes [onChunkAvailable] every ~100ms with base64-encoded PCM bytes and
     * a normalized audio peak level in [0.0, 1.0] for the UI visualizer.
     *
     * @return true if AudioRecord initialized and started successfully, false otherwise.
     */
    fun start(onChunkAvailable: (pcmBase64: String, normalizedLevel: Float) -> Unit): Boolean

    /**
     * Stops audio capture and releases native resources. Idempotent.
     */
    fun stop()
}
