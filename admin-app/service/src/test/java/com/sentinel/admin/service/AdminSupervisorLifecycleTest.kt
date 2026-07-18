package com.sentinel.admin.service

import com.sentinel.admin.data.remote.ReconnectPolicy
import com.sentinel.admin.data.remote.SequenceGenerator
import com.sentinel.admin.data.remote.protocol.MessageSerializer
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.ConnectionState
import com.sentinel.admin.domain.model.ReconnectConfig
import com.sentinel.admin.domain.repository.AuthRepository
import com.sentinel.admin.domain.repository.ConnectionRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Full lifecycle integration test for AdminSupervisor.
 *
 * Verifies the complete cycle:
 *   AUTH_ACK → Heartbeat starts → Heartbeat timeout →
 *   Reconnect → AUTH again → Heartbeat starts again
 *
 * Uses test doubles for ConnectionRepository and AuthRepository.
 * Uses FakeClock for deterministic time.
 * Uses TestScope with StandardTestDispatcher for controlled time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminSupervisorLifecycleTest {

    // ============================================================
    // Test doubles
    // ============================================================

    private class FakeConnectionRepository : ConnectionRepository {
        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val state: StateFlow<ConnectionState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
        override val events: SharedFlow<ConnectionEvent> = _events.asSharedFlow()

        val sentMessages = mutableListOf<String>()
        var connectCount = 0
        var disconnectCount = 0

        override suspend fun connect(serverUrl: String) {
            connectCount++
        }

        override suspend fun disconnect() {
            disconnectCount++
        }

        override fun sendText(message: String): Boolean {
            sentMessages.add(message)
            return true
        }

        override fun sendBinary(data: ByteArray): Boolean = true

        /** Simulate server sending an event */
        fun emitEvent(event: ConnectionEvent) {
            _events.tryEmit(event)
        }
    }

    private class FakeAuthRepository(private var token: String? = "test-jwt") : AuthRepository {
        override fun getToken(): String? = token
        override fun saveToken(token: String) { this.token = token }
        override fun clearToken() { token = null }
    }

    // ============================================================
    // Setup
    // ============================================================

    private lateinit var testScope: TestScope
    private lateinit var fakeConnectionRepo: FakeConnectionRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var fakeClock: FakeClock
    private lateinit var sequenceGenerator: SequenceGenerator
    private lateinit var heartbeatScheduler: HeartbeatScheduler
    private lateinit var supervisor: AdminSupervisor

    @Before
    fun setUp() {
        testScope = TestScope(StandardTestDispatcher())
        fakeConnectionRepo = FakeConnectionRepository()
        fakeAuthRepo = FakeAuthRepository()
        fakeClock = FakeClock(startTime = 1_000_000L)
        sequenceGenerator = SequenceGenerator()

        val serializer = MessageSerializer(Moshi.Builder().build())

        heartbeatScheduler = HeartbeatScheduler(
            connectionRepository = fakeConnectionRepo,
            messageSerializer = serializer,
            sequenceGenerator = sequenceGenerator,
            scope = testScope,
            clock = fakeClock,
            intervalMs = 20_000L,
            timeoutMs = 10_000L
        )

        supervisor = AdminSupervisor(
            connectionRepository = fakeConnectionRepo,
            authRepository = fakeAuthRepo,
            heartbeatScheduler = heartbeatScheduler,
            messageSerializer = serializer,
            sequenceGenerator = sequenceGenerator,
            reconnectPolicy = ReconnectPolicy(
                config = ReconnectConfig(
                    initialDelayMs = 1_000L,
                    maxDelayMs = 5_000L,
                    maxAttempts = 3,
                    multiplier = 2.0,
                    jitterFactor = 0.0 // deterministic
                )
            ),
            scope = testScope,
            clock = fakeClock
        )
    }

    // ============================================================
    // Full lifecycle test
    // ============================================================

    @Test
    fun `full lifecycle - connect, auth, heartbeat, timeout, reconnect, auth again`() = testScope.runTest {
        // ---- Phase 1: Start supervisor ----
        supervisor.start("ws://test:8080/ws")
        advanceTimeBy(1) // let coroutines start

        assertEquals(1, fakeConnectionRepo.connectCount)
        assertEquals(ConnectionState.Connecting, supervisor.connectionState.value)

        // ---- Phase 2: WebSocket connects → triggers auth ----
        fakeConnectionRepo.emitEvent(ConnectionEvent.Connected)
        advanceTimeBy(1) // process event

        assertEquals(ConnectionState.Authenticating, supervisor.connectionState.value)
        assertTrue("AUTH message should be sent", fakeConnectionRepo.sentMessages.any {
            it.contains("\"type\":\"AUTH\"")
        })

        // ---- Phase 3: AUTH_ACK → Ready, heartbeat starts ----
        fakeConnectionRepo.emitEvent(ConnectionEvent.AuthResult(success = true))
        advanceTimeBy(1) // process event

        assertEquals(ConnectionState.Ready, supervisor.connectionState.value)
        assertTrue("Heartbeat should be running", heartbeatScheduler.isRunning)

        // ---- Phase 4: Advance past heartbeat interval → heartbeat sent ----
        fakeClock.advanceBy(20_000L)
        advanceTimeBy(20_001L) // past heartbeat interval

        val heartbeatsSent = fakeConnectionRepo.sentMessages.count {
            it.contains("\"type\":\"HEARTBEAT\"")
        }
        assertTrue("At least one heartbeat should be sent, got $heartbeatsSent", heartbeatsSent >= 1)

        // ---- Phase 5: No ACK → timeout → reconnect ----
        // Don't call onAckReceived(). Wait for timeout.
        fakeClock.advanceBy(10_000L)
        advanceTimeBy(10_001L) // past timeout

        // Heartbeat timeout emits ServerError → supervisor schedules reconnect
        val currentState = supervisor.connectionState.value
        assertTrue(
            "Should be Reconnecting after timeout, was: $currentState",
            currentState is ConnectionState.Reconnecting
        )

        // ---- Phase 6: Wait for reconnect delay (1s with jitter=0) ----
        advanceTimeBy(1_500L)

        // Should have attempted second connect
        assertTrue(
            "Should have reconnected, connectCount=${fakeConnectionRepo.connectCount}",
            fakeConnectionRepo.connectCount >= 2
        )

        // ---- Phase 7: WebSocket reconnects → auth again ----
        fakeConnectionRepo.emitEvent(ConnectionEvent.Connected)
        advanceTimeBy(1)

        assertEquals(ConnectionState.Authenticating, supervisor.connectionState.value)

        // Verify AUTH sent again
        val authCount = fakeConnectionRepo.sentMessages.count {
            it.contains("\"type\":\"AUTH\"")
        }
        assertTrue("Should have sent AUTH twice, count=$authCount", authCount >= 2)

        // ---- Phase 8: AUTH_ACK again → Ready, heartbeat restarts ----
        fakeConnectionRepo.emitEvent(ConnectionEvent.AuthResult(success = true))
        advanceTimeBy(1)

        assertEquals(ConnectionState.Ready, supervisor.connectionState.value)
        assertTrue("Heartbeat should be running again", heartbeatScheduler.isRunning)

        // ---- Cleanup ----
        supervisor.stop()
        advanceTimeBy(1)

        assertEquals(ConnectionState.Disconnected, supervisor.connectionState.value)
        assertFalse("Heartbeat should be stopped", heartbeatScheduler.isRunning)
    }

    // ============================================================
    // Sequence monotonicity across reconnect
    // ============================================================

    @Test
    fun `sequence numbers are monotonically increasing across reconnects`() = testScope.runTest {
        supervisor.start("ws://test:8080/ws")
        advanceTimeBy(1)

        // First connection: AUTH
        fakeConnectionRepo.emitEvent(ConnectionEvent.Connected)
        advanceTimeBy(1)

        // Get first AUTH sequence
        val firstAuth = fakeConnectionRepo.sentMessages.first { it.contains("AUTH") }

        // Simulate disconnect + reconnect
        fakeConnectionRepo.emitEvent(ConnectionEvent.Disconnected)
        advanceTimeBy(1)
        advanceTimeBy(1_500L) // wait for reconnect delay

        // Second connection: AUTH again
        fakeConnectionRepo.emitEvent(ConnectionEvent.Connected)
        advanceTimeBy(1)

        val secondAuth = fakeConnectionRepo.sentMessages.last { it.contains("AUTH") }

        // Extract sequence numbers
        val seq1 = Regex(""""sequence":(\d+)""").find(firstAuth)?.groupValues?.get(1)?.toLong() ?: -1
        val seq2 = Regex(""""sequence":(\d+)""").find(secondAuth)?.groupValues?.get(1)?.toLong() ?: -1

        assertTrue(
            "Second AUTH sequence ($seq2) should be greater than first ($seq1)",
            seq2 > seq1
        )

        supervisor.stop()
    }

    // ============================================================
    // Auth failure
    // ============================================================

    @Test
    fun `auth failure sets Error state`() = testScope.runTest {
        supervisor.start("ws://test:8080/ws")
        advanceTimeBy(1)

        fakeConnectionRepo.emitEvent(ConnectionEvent.Connected)
        advanceTimeBy(1)

        fakeConnectionRepo.emitEvent(ConnectionEvent.AuthResult(success = false, error = "Invalid token"))
        advanceTimeBy(1)

        val state = supervisor.connectionState.value
        assertTrue("Should be in Error state", state is ConnectionState.Error)
        assertEquals("Invalid token", (state as ConnectionState.Error).message)

        supervisor.stop()
    }

    // ============================================================
    // Missing token
    // ============================================================

    @Test
    fun `missing token sets Error state`() = testScope.runTest {
        fakeAuthRepo.clearToken()

        supervisor.start("ws://test:8080/ws")
        advanceTimeBy(1)

        fakeConnectionRepo.emitEvent(ConnectionEvent.Connected)
        advanceTimeBy(1)

        val state = supervisor.connectionState.value
        assertTrue("Should be in Error state", state is ConnectionState.Error)
        assertEquals("No auth token", (state as ConnectionState.Error).message)

        supervisor.stop()
    }

    // ============================================================
    // Reconnect exhaustion
    // ============================================================

    @Test
    fun `reconnect exhaustion sets Error state`() = testScope.runTest {
        supervisor.start("ws://test:8080/ws")
        advanceTimeBy(1)

        // Connect and authenticate
        fakeConnectionRepo.emitEvent(ConnectionEvent.Connected)
        advanceTimeBy(1)
        fakeConnectionRepo.emitEvent(ConnectionEvent.AuthResult(success = true))
        advanceTimeBy(1)

        // Simulate 3 disconnects (maxAttempts=3)
        repeat(3) {
            fakeConnectionRepo.emitEvent(ConnectionEvent.Disconnected)
            advanceTimeBy(10_000L) // past any reconnect delay
        }

        // After 3 failures, should be exhausted
        fakeConnectionRepo.emitEvent(ConnectionEvent.Disconnected)
        advanceTimeBy(1)

        val state = supervisor.connectionState.value
        assertTrue(
            "Should be Error after exhaustion, was: $state",
            state is ConnectionState.Error
        )

        supervisor.stop()
    }
}
