package com.sentinel.admin.service

import android.util.Log
import com.sentinel.admin.data.remote.SequenceGenerator
import com.sentinel.admin.data.remote.protocol.MessageSerializer
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.repository.ConnectionRepository
import com.sentinel.admin.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Periodically sends HEARTBEAT messages and detects server unresponsiveness.
 *
 * Lifecycle:
 * - [start] when ConnectionState becomes Ready.
 * - [stop] on disconnect, reconnect, or user stop.
 *
 * Timeout detection:
 * - If no HEARTBEAT_ACK is received within [timeoutMs], emits a timeout event.
 * - The supervisor handles timeout events (e.g., triggering reconnect).
 *
 * Uses [Clock] abstraction to enable deterministic testing via [FakeClock].
 */
class HeartbeatScheduler(
    private val connectionRepository: ConnectionRepository,
    private val messageSerializer: MessageSerializer,
    private val sequenceGenerator: SequenceGenerator,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {

    companion object {
        private const val TAG = "Sentinel:AdminHB"
        const val DEFAULT_INTERVAL_MS = 20_000L
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }

    private val _events = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ConnectionEvent> = _events.asSharedFlow()

    private var heartbeatJob: Job? = null

    /** Timestamp of last sent heartbeat (epoch ms). 0 if none sent. */
    private val _lastSentTimestamp = AtomicLong(0L)
    val lastSentTimestamp: Long get() = _lastSentTimestamp.get()

    /** Timestamp of last received ACK (epoch ms). 0 if none received. */
    private val _lastAckTimestamp = AtomicLong(0L)
    val lastAckTimestamp: Long get() = _lastAckTimestamp.get()

    /** Whether the scheduler is currently running. */
    val isRunning: Boolean get() = heartbeatJob?.isActive == true

    /**
     * Starts the heartbeat loop.
     * Safe to call multiple times — cancels any existing loop first.
     */
    fun start() {
        stop() // Prevent duplicate timers

        heartbeatJob = scope.launch {
            Log.i(TAG, "Heartbeat started (interval=${intervalMs}ms, timeout=${timeoutMs}ms)")
            Log.d(TAG, "Heartbeat coroutine launched on ${Thread.currentThread().name}")

            while (isActive) {
                Log.d(TAG, "Heartbeat waiting ${intervalMs}ms...")
                delay(intervalMs)

                if (!isActive) break

                sendHeartbeat()

                // Schedule timeout check
                val sentAt = _lastSentTimestamp.get()
                delay(timeoutMs)

                // Check if ACK arrived since we sent
                if (isActive && _lastAckTimestamp.get() < sentAt) {
                    Log.w(TAG, "Heartbeat timeout — no ACK within ${timeoutMs}ms")
                    _events.tryEmit(ConnectionEvent.ServerError(0, "Heartbeat timeout"))
                    break // Stop loop — supervisor handles reconnect
                }
            }
        }
    }

    /**
     * Stops the heartbeat loop.
     */
    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.i(TAG, "Heartbeat stopped")
    }

    /**
     * Called when a HEARTBEAT_ACK is received.
     * Updates the last ACK timestamp.
     */
    fun onAckReceived() {
        _lastAckTimestamp.set(clock.currentTimeMillis())
        Log.d(TAG, "Heartbeat ACK received")
    }

    /**
     * Resets timestamps. Called on new connection.
     */
    fun reset() {
        _lastSentTimestamp.set(0L)
        _lastAckTimestamp.set(0L)
    }

    private fun sendHeartbeat() {
        val sequence = sequenceGenerator.next()
        val message = messageSerializer.serializeHeartbeat(sequence)
        val sent = connectionRepository.sendText(message)

        if (sent) {
            _lastSentTimestamp.set(clock.currentTimeMillis())
            Log.d(TAG, "Heartbeat sent (seq=$sequence)")
        } else {
            Log.w(TAG, "Failed to send heartbeat")
        }
    }
}
