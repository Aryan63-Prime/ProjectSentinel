package com.sentinel.host.service

import com.sentinel.host.data.remote.SequenceGenerator
import com.sentinel.host.data.remote.protocol.MessageSerializer
import com.sentinel.host.domain.model.ConnectionEvent
import com.sentinel.host.domain.model.ConnectionState
import com.sentinel.host.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeartbeatSchedulerTest {

    private lateinit var testScope: TestScope
    private lateinit var fakeRepo: FakeHeartbeatRepo
    private lateinit var serializer: MessageSerializer
    private lateinit var sequenceGenerator: SequenceGenerator
    private lateinit var scheduler: HeartbeatScheduler

    @Before
    fun setUp() {
        testScope = TestScope()
        fakeRepo = FakeHeartbeatRepo()
        serializer = MessageSerializer()
        sequenceGenerator = SequenceGenerator()

        scheduler = HeartbeatScheduler(
            connectionRepository = fakeRepo,
            messageSerializer = serializer,
            sequenceGenerator = sequenceGenerator,
            scope = testScope,
            intervalMs = 1000, // 1s for testing
            timeoutMs = 500   // 500ms timeout for testing
        )
    }

    @Test
    fun `initial state is not running`() {
        assertFalse(scheduler.isRunning)
        assertEquals(0L, scheduler.lastSentTimestamp)
        assertEquals(0L, scheduler.lastAckTimestamp)
    }

    @Test
    fun `start makes scheduler running`() {
        scheduler.start()
        assertTrue(scheduler.isRunning)
    }

    @Test
    fun `stop makes scheduler not running`() {
        scheduler.start()
        scheduler.stop()
        assertFalse(scheduler.isRunning)
    }

    @Test
    fun `heartbeat is sent after interval`() {
        scheduler.start()

        // Advance past the interval (1000ms)
        testScope.advanceTimeBy(1001)

        assertTrue("Expected heartbeat to be sent", fakeRepo.sentMessages.isNotEmpty())
        assertTrue(fakeRepo.sentMessages.first().contains("HEARTBEAT"))
    }

    @Test
    fun `no heartbeat sent before interval elapses`() {
        scheduler.start()

        // Only advance 500ms (interval is 1000ms)
        testScope.advanceTimeBy(500)

        assertTrue("No heartbeat should be sent yet", fakeRepo.sentMessages.isEmpty())
    }

    @Test
    fun `timeout emits error when no ACK received`() {
        var timeoutEvent: ConnectionEvent? = null
        testScope.launch {
            scheduler.events.collect { timeoutEvent = it }
        }

        scheduler.start()

        // Advance past interval (1000ms) — sends heartbeat
        testScope.advanceTimeBy(1001)
        assertTrue(fakeRepo.sentMessages.isNotEmpty())

        // Advance past timeout (500ms) — no ACK, triggers error
        testScope.advanceTimeBy(501)

        assertNotNull("Timeout event should be emitted", timeoutEvent)
        assertTrue(timeoutEvent is ConnectionEvent.Error)
        assertEquals("Heartbeat timeout", (timeoutEvent as ConnectionEvent.Error).message)
    }

    @Test
    fun `no timeout when ACK received in time`() {
        var timeoutEvent: ConnectionEvent? = null
        testScope.launch {
            scheduler.events.collect { timeoutEvent = it }
        }

        scheduler.start()

        // Advance past interval — sends heartbeat
        testScope.advanceTimeBy(1001)

        // Simulate ACK received
        scheduler.onAckReceived()

        // Advance past timeout — ACK was received, no error
        testScope.advanceTimeBy(501)

        // Should continue to next cycle, not emit error
        // The loop continues — advance past next interval
        testScope.advanceTimeBy(1001)
        assertTrue("Second heartbeat should be sent", fakeRepo.sentMessages.size >= 2)
    }

    @Test
    fun `onAckReceived updates timestamp`() {
        assertEquals(0L, scheduler.lastAckTimestamp)
        scheduler.onAckReceived()
        assertTrue(scheduler.lastAckTimestamp > 0)
    }

    @Test
    fun `reset clears timestamps`() {
        scheduler.onAckReceived()
        assertTrue(scheduler.lastAckTimestamp > 0)

        scheduler.reset()
        assertEquals(0L, scheduler.lastSentTimestamp)
        assertEquals(0L, scheduler.lastAckTimestamp)
    }

    @Test
    fun `start cancels previous scheduler - no duplicates`() {
        scheduler.start()
        assertTrue(scheduler.isRunning)

        // Start again — should cancel previous and start new
        scheduler.start()
        assertTrue(scheduler.isRunning)

        scheduler.stop()
        assertFalse(scheduler.isRunning)
    }

    @Test
    fun `stop is idempotent`() {
        scheduler.stop()
        scheduler.stop()
        assertFalse(scheduler.isRunning)
    }

    @Test
    fun `scheduler stops after timeout`() {
        scheduler.start()
        assertTrue(scheduler.isRunning)

        // Advance through interval + timeout → loop breaks
        testScope.advanceTimeBy(1001)
        testScope.advanceTimeBy(501)

        // Scheduler should have stopped itself
        assertFalse(scheduler.isRunning)
    }
}

// ================================================================
// Test fake
// ================================================================

internal class FakeHeartbeatRepo : ConnectionRepository {
    val sentMessages = mutableListOf<String>()

    override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Ready)
    override val events: SharedFlow<ConnectionEvent> = MutableSharedFlow(extraBufferCapacity = 64)

    override suspend fun connect(serverUrl: String) {}
    override suspend fun disconnect() {}

    override fun sendText(message: String): Boolean {
        sentMessages.add(message)
        return true
    }

    override fun sendBinary(data: ByteArray): Boolean = true
}
