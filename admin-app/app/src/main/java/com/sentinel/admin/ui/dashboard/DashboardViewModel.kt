package com.sentinel.admin.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Dashboard screen.
 *
 * Responsibilities:
 * - Loads devices from DeviceRepository (REST)
 * - Supports manual and pull-to-refresh
 * - Client-side search (name, ID, model)
 * - Sorting (status, alpha, battery, heartbeat)
 * - Exposes immutable [DashboardUiState]
 *
 * No Android Context. No networking logic. No WebSocket.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
        observeLiveUpdates()
    }

    /**
     * Observes the repository's live device map for WebSocket updates.
     * Only updates the list — does not trigger loading/refresh states.
     */
    private fun observeLiveUpdates() {
        viewModelScope.launch {
            deviceRepository.devices.collect { deviceMap ->
                if (deviceMap.isNotEmpty()) {
                    _uiState.update {
                        it.copy(devices = deviceMap.values.toList())
                    }
                    applyFilterAndSort()
                }
            }
        }
    }

    // ============================================================
    // User actions
    // ============================================================

    /**
     * Initial load or manual toolbar refresh.
     */
    fun loadDevices() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            deviceRepository.getDevices()
                .onSuccess { devices ->
                    _uiState.update {
                        it.copy(
                            devices = devices,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                    applyFilterAndSort()
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

    /**
     * Pull-to-refresh action.
     */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            deviceRepository.getDevices()
                .onSuccess { devices ->
                    _uiState.update {
                        it.copy(
                            devices = devices,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                    applyFilterAndSort()
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

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilterAndSort()
    }

    fun onSortOrderChanged(sortOrder: SortOrder) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        applyFilterAndSort()
    }

    fun onViewModeChanged(viewMode: ViewMode) {
        _uiState.update { it.copy(viewMode = viewMode) }
    }

    fun retry() {
        loadDevices()
    }

    // ============================================================
    // Internal
    // ============================================================

    internal fun applyFilterAndSort() {
        val state = _uiState.value
        val filtered = filterDevices(state.devices, state.searchQuery)
        val sorted = sortDevices(filtered, state.sortOrder)
        val markers = buildMarkers(sorted)
        _uiState.update { it.copy(displayDevices = sorted, markers = markers) }
    }

    internal fun filterDevices(devices: List<Device>, query: String): List<Device> {
        if (query.isBlank()) return devices
        val q = query.trim().lowercase()
        return devices.filter { device ->
            device.deviceName.lowercase().contains(q) ||
                    device.deviceId.lowercase().contains(q) ||
                    device.model.lowercase().contains(q)
        }
    }

    internal fun sortDevices(devices: List<Device>, sortOrder: SortOrder): List<Device> {
        return when (sortOrder) {
            SortOrder.STATUS_THEN_NAME -> devices.sortedWith(
                compareByDescending<Device> { it.heartbeatStatus == "online" }
                    .thenBy { it.deviceName.lowercase() }
            )
            SortOrder.ALPHABETICAL -> devices.sortedBy { it.deviceName.lowercase() }
            SortOrder.BATTERY -> devices.sortedByDescending {
                it.latestLocation?.battery ?: -1
            }
            SortOrder.LAST_HEARTBEAT -> devices.sortedByDescending {
                it.lastHeartbeat
            }
        }
    }
    internal fun buildMarkers(devices: List<Device>): List<DeviceMarker> {
        return devices.mapNotNull { device ->
            device.latestLocation?.let { loc ->
                DeviceMarker(
                    deviceId = device.deviceId,
                    deviceName = device.deviceName,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    isOnline = device.heartbeatStatus == "online",
                    battery = loc.battery,
                    network = loc.network
                )
            }
        }
    }
}
