package com.sentinel.admin.ui.dashboard

import com.sentinel.admin.domain.model.Device

/**
 * Immutable UI state for the Dashboard screen.
 *
 * Produced by [DashboardViewModel], consumed by [DashboardScreen].
 */
data class DashboardUiState(
    /** All devices from the API (unfiltered). */
    val devices: List<Device> = emptyList(),
    /** Filtered and sorted devices displayed in the list. */
    val displayDevices: List<Device> = emptyList(),
    /** Map markers for devices with locations. */
    val markers: List<DeviceMarker> = emptyList(),
    /** Current view mode (List or Map). */
    val viewMode: ViewMode = ViewMode.LIST,
    /** Current search query. */
    val searchQuery: String = "",
    /** Current sort order. */
    val sortOrder: SortOrder = SortOrder.STATUS_THEN_NAME,
    /** Whether devices are loading. */
    val isLoading: Boolean = false,
    /** Whether a refresh is in progress. */
    val isRefreshing: Boolean = false,
    /** Error message, null if no error. */
    val errorMessage: String? = null
) {
    /** True if no devices after loading completes. */
    val isEmpty: Boolean
        get() = !isLoading && devices.isEmpty() && errorMessage == null

    /** True if there are devices but search returned no results. */
    val isSearchEmpty: Boolean
        get() = !isLoading && devices.isNotEmpty() && displayDevices.isEmpty()
}

/**
 * Dashboard view modes.
 */
enum class ViewMode {
    LIST, MAP
}

/**
 * Sort orders for the device list.
 */
enum class SortOrder(val label: String) {
    STATUS_THEN_NAME("Status"),
    ALPHABETICAL("A-Z"),
    BATTERY("Battery"),
    LAST_HEARTBEAT("Heartbeat")
}
