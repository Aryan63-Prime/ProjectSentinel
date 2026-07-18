package com.sentinel.admin.ui.detail

import com.sentinel.admin.domain.model.AudioStatistics
import com.sentinel.admin.domain.model.Device
import com.sentinel.admin.domain.model.PlaybackState

/**
 * Immutable UI state for the Device Detail screen.
 *
 * Produced by [DeviceDetailViewModel], consumed by [DeviceDetailScreen].
 */
data class DeviceDetailUiState(
    /** The loaded device, null until first successful load. */
    val device: Device? = null,
    /** Whether the device is loading for the first time. */
    val isLoading: Boolean = false,
    /** Whether a pull-to-refresh is in progress. */
    val isRefreshing: Boolean = false,
    /** Error message, null if no error. */
    val errorMessage: String? = null,
    /** Audio playback state — observed from AudioMonitor, not owned by ViewModel. */
    val playbackState: PlaybackState = PlaybackState.Idle,
    /** Audio statistics — observed from AudioMonitor. */
    val audioStats: AudioStatistics = AudioStatistics()
) {
    /** True if device loaded successfully. */
    val hasDevice: Boolean get() = device != null && errorMessage == null

    /** True if device is online. */
    val isOnline: Boolean get() = device?.heartbeatStatus == "online"
}
