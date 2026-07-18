package com.sentinel.admin.ui.detail

import com.sentinel.admin.data.audio.AudioOutput
import com.sentinel.admin.data.audio.NativeOpusDecoder
import com.sentinel.admin.domain.model.AudioStatistics
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceLocation
import com.sentinel.admin.domain.model.DeviceUpdateEvent
import com.sentinel.admin.domain.model.EventStatistics
import com.sentinel.admin.domain.model.PlaybackState
import com.sentinel.admin.domain.repository.AudioRepository
import com.sentinel.admin.domain.repository.DeviceRepository
import com.sentinel.admin.service.AudioMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.lifecycle.SavedStateHandle

/**
 * Unit tests for [DeviceDetailViewModel].
 *
 * Covers:
 * - Initial load success
 * - Initial load error
 * - Refresh success
 * - Refresh error (preserves existing device)
 * - Retry after error
 * - Loading states
 * - UI state computed properties
 * - Not found error
 * - Unauthorized error
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDetailViewModelTest {

    // ============================================================
    // Test doubles
    // ============================================================

    private class FakeDeviceRepository : DeviceRepository {
        var deviceResult: Result<Device> = Result.success(TEST_DEVICE)
        var devicesResult: Result<List<Device>> = Result.success(emptyList())
        var getDeviceCallCount = 0

        override val devices: StateFlow<Map<String, Device>> = MutableStateFlow(emptyMap())
        override val deviceUpdates: SharedFlow<DeviceUpdateEvent> = MutableSharedFlow()
        override val eventStatistics: StateFlow<EventStatistics> = MutableStateFlow(EventStatistics())

        override suspend fun getDevice(deviceId: String): Result<Device> {
            getDeviceCallCount++
            return deviceResult
        }

        override suspend fun getDevices(): Result<List<Device>> {
            return devicesResult
        }
    }

    private class FakeAudioRepository : AudioRepository {
        var listenCalls = mutableListOf<String>()
        var stopCalls = mutableListOf<String>()

        override fun listen(deviceId: String): Boolean {
            listenCalls.add(deviceId)
            return true
        }

        override fun stopListening(deviceId: String): Boolean {
            stopCalls.add(deviceId)
            return true
        }
    }

    private class FakeAudioOutput : AudioOutput {
        override fun initialize() = true
        override fun write(pcm: ShortArray, offset: Int, size: Int) = size
        override fun pause() {}
        override fun resume() {}
        override fun release() {}
    }

    // ============================================================
    // Setup
    // ============================================================

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeDeviceRepository
    private lateinit var fakeAudioRepo: FakeAudioRepository
    private lateinit var audioMonitor: AudioMonitor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeDeviceRepository()
        fakeAudioRepo = FakeAudioRepository()
        audioMonitor = AudioMonitor(
            decoder = NativeOpusDecoder(),
            audioOutput = FakeAudioOutput(),
            scope = TestScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(deviceId: String = "HOST-0001"): DeviceDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("deviceId" to deviceId))
        return DeviceDetailViewModel(savedStateHandle, fakeRepo, fakeAudioRepo, audioMonitor)
    }

    // ============================================================
    // Initial load
    // ============================================================

    @Test
    fun `initial state is loading`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `load success shows device`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.success(TEST_DEVICE)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.device)
        assertEquals("HOST-0001", state.device!!.deviceId)
        assertEquals("Pixel 9", state.device!!.deviceName)
        assertNull(state.errorMessage)
    }

    @Test
    fun `load success with location`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.success(TEST_DEVICE_WITH_LOCATION)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val loc = viewModel.uiState.value.device!!.latestLocation
        assertNotNull(loc)
        assertEquals(28.6139, loc!!.latitude, 0.0001)
        assertEquals(81, loc.battery)
    }

    @Test
    fun `load error shows message`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.failure(RuntimeException("Server error"))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.device)
        assertEquals("Server error", state.errorMessage)
    }

    @Test
    fun `load 404 shows not found`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.failure(NoSuchElementException("Device not found"))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errorMessage!!.contains("not found"))
    }

    @Test
    fun `load 401 shows unauthorized`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.failure(
            IllegalStateException("Unauthorized — invalid or expired token")
        )
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Unauthorized"))
    }

    // ============================================================
    // Refresh
    // ============================================================

    @Test
    fun `refresh sets isRefreshing`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.success(TEST_DEVICE)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isRefreshing)

        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh updates device`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.success(TEST_DEVICE)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Pixel 9", viewModel.uiState.value.device!!.deviceName)

        fakeRepo.deviceResult = Result.success(
            TEST_DEVICE.copy(deviceName = "Pixel 9 Pro")
        )
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Pixel 9 Pro", viewModel.uiState.value.device!!.deviceName)
    }

    @Test
    fun `refresh error preserves existing device`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.success(TEST_DEVICE)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        fakeRepo.deviceResult = Result.failure(RuntimeException("Network error"))
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        // Device preserved, error shown
        assertNotNull(viewModel.uiState.value.device)
        assertEquals("Network error", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `refresh clears previous error on success`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.success(TEST_DEVICE)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Trigger error
        fakeRepo.deviceResult = Result.failure(RuntimeException("fail"))
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Successful refresh clears error
        fakeRepo.deviceResult = Result.success(TEST_DEVICE)
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ============================================================
    // Retry
    // ============================================================

    @Test
    fun `retry after error reloads`() = runTest(testDispatcher) {
        fakeRepo.deviceResult = Result.failure(RuntimeException("fail"))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        fakeRepo.deviceResult = Result.success(TEST_DEVICE)
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertNotNull(viewModel.uiState.value.device)
    }

    @Test
    fun `retry calls getDevice`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val initialCount = fakeRepo.getDeviceCallCount

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialCount + 1, fakeRepo.getDeviceCallCount)
    }

    // ============================================================
    // UI State computed properties
    // ============================================================

    @Test
    fun `hasDevice true when device loaded`() {
        val state = DeviceDetailUiState(device = TEST_DEVICE)
        assertTrue(state.hasDevice)
    }

    @Test
    fun `hasDevice false when no device`() {
        val state = DeviceDetailUiState(device = null)
        assertFalse(state.hasDevice)
    }

    @Test
    fun `hasDevice false when error with no device`() {
        val state = DeviceDetailUiState(device = null, errorMessage = "error")
        assertFalse(state.hasDevice)
    }

    @Test
    fun `isOnline true when heartbeatStatus online`() {
        val state = DeviceDetailUiState(device = TEST_DEVICE)
        assertTrue(state.isOnline)
    }

    @Test
    fun `isOnline false when heartbeatStatus offline`() {
        val state = DeviceDetailUiState(
            device = TEST_DEVICE.copy(heartbeatStatus = "offline")
        )
        assertFalse(state.isOnline)
    }

    @Test
    fun `isOnline false when no device`() {
        val state = DeviceDetailUiState(device = null)
        assertFalse(state.isOnline)
    }

    // ============================================================
    // Audio
    // ============================================================

    @Test
    fun `initial playback state is Idle`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PlaybackState.Idle, viewModel.uiState.value.playbackState)
    }

    @Test
    fun `initial audio stats are zero`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AudioStatistics(), viewModel.uiState.value.audioStats)
    }

    @Test
    fun `onListenClick sends listen command`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onListenClick()
        assertEquals(1, fakeAudioRepo.listenCalls.size)
        assertEquals("HOST-0001", fakeAudioRepo.listenCalls[0])
    }

    @Test
    fun `onStopClick sends stop command`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopClick()
        assertEquals(1, fakeAudioRepo.stopCalls.size)
        assertEquals("HOST-0001", fakeAudioRepo.stopCalls[0])
    }

    // ============================================================
    // Test data
    // ============================================================

    companion object {
        private val TEST_DEVICE = Device(
            deviceId = "HOST-0001",
            connectionId = "CONN-001",
            authenticated = true,
            registered = true,
            registrationState = "registered",
            heartbeatStatus = "online",
            connectedAt = "2026-07-09T12:00:00Z",
            lastHeartbeat = "2026-07-09T12:00:20Z",
            deviceName = "Pixel 9",
            appVersion = "1.0.0",
            model = "Google Pixel",
            latestLocation = null
        )

        private val TEST_DEVICE_WITH_LOCATION = TEST_DEVICE.copy(
            latestLocation = DeviceLocation(
                deviceId = "HOST-0001",
                latitude = 28.6139,
                longitude = 77.2090,
                accuracy = 5.4,
                battery = 81,
                network = "5G",
                recordedAt = "2026-07-09T12:00:10Z"
            )
        )
    }
}
