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
    private val audioMonitor: AudioMonitor
) : ViewModel() {

    private val deviceId: String = savedStateHandle.get<String>("deviceId")
        ?: throw IllegalArgumentException("deviceId is required")

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        loadDevice()
        observeAudioState()
        observeLiveUpdates()
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
        audioRepository.stopListening(deviceId)
        audioMonitor.stop()
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
    }
}
