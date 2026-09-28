# Feature Specification & Code: Predictive Battery & Thermal Throttling Engine

## 1. Overview & Problem Statement
Heavy background tasks—such as high-frequency GPS tracking, continuous Opus microphone encoding, and periodic camera captures—generate significant device heat and battery drain. On budget hardware or in hot environments (e.g. dashboards in direct sunlight), unthrottled execution triggers OS thermal shutdowns or kills background foreground services.

This feature introduces an automated **Thermal & Battery Protection Engine** (`PowerThrottleManager.kt`). It listens to Android’s native **`PowerManager.OnThermalStatusChangedListener`** (API 29+) and battery broadcasts, dynamically scaling down execution workloads across four distinct operational tiers.

---

## 2. Dynamic Operational Tiers

| Thermal Status | Battery Level | GPS Interval | Audio Streaming | Camera Resolution | Admin Alert |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`NORMAL`** | > 30% | 5 seconds | 16 kHz Continuous | 1080p | None |
| **`MODERATE`** | 15% – 30% | 15 seconds | 16 kHz | 1080p | None |
| **`SEVERE`** | 5% – 15% | 30 seconds | Suspended (On-Demand only) | 720p | `THERMAL_WARNING` |
| **`CRITICAL`** | < 5% | 60 seconds (Passive) | Disabled | Disabled | `BATTERY_CRITICAL` |

---

## 3. Host-App Implementation

### File: `host-app/data/src/main/java/com/sentinel/host/data/device/PowerThrottleManager.kt`

```kotlin
package com.sentinel.host.data.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThrottleLevel {
    NORMAL,
    MODERATE,
    SEVERE,
    CRITICAL
}

data class PowerState(
    val throttleLevel: ThrottleLevel = ThrottleLevel.NORMAL,
    val batteryPct: Int = 100,
    val batteryTempC: Double = 25.0,
    val isCharging: Boolean = false,
    val thermalStatusInt: Int = 0
)

@Singleton
class PowerThrottleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:PowerThrottle"
    }

    private val _powerState = MutableStateFlow(PowerState())
    val powerState: StateFlow<PowerState> = _powerState.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatteryMetrics(intent)
            }
        }
    }

    init {
        // Register Battery Broadcast
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(batteryReceiver, filter)
        initialIntent?.let { updateBatteryMetrics(it) }

        // Register Thermal Listener (Android 10+ / API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.addThermalStatusListener { status ->
                handleThermalChange(status)
            }
        }
    }

    private fun updateBatteryMetrics(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val batteryTempC = tempTenths / 10.0

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

        val computedLevel = computeThrottleLevel(
            thermalStatus = _powerState.value.thermalStatusInt,
            batteryPct = batteryPct,
            isCharging = isCharging
        )

        _powerState.value = _powerState.value.copy(
            throttleLevel = computedLevel,
            batteryPct = batteryPct,
            batteryTempC = batteryTempC,
            isCharging = isCharging
        )

        Log.d(TAG, "Battery updated: $batteryPct%, Temp: $batteryTempC°C, Level: $computedLevel")
    }

    private fun handleThermalChange(status: Int) {
        val computedLevel = computeThrottleLevel(
            thermalStatus = status,
            batteryPct = _powerState.value.batteryPct,
            isCharging = _powerState.value.isCharging
        )

        _powerState.value = _powerState.value.copy(
            throttleLevel = computedLevel,
            thermalStatusInt = status
        )

        Log.w(TAG, "Thermal Status changed: $status -> ThrottleLevel: $computedLevel")
    }

    private fun computeThrottleLevel(
        thermalStatus: Int,
        batteryPct: Int,
        isCharging: Boolean
    ): ThrottleLevel {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (thermalStatus) {
                PowerManager.THERMAL_STATUS_SHUTDOWN,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_CRITICAL -> return ThrottleLevel.CRITICAL
                PowerManager.THERMAL_STATUS_SEVERE -> return ThrottleLevel.SEVERE
                PowerManager.THERMAL_STATUS_MODERATE -> return ThrottleLevel.MODERATE
            }
        }

        // Battery percentage fallback thresholds
        return when {
            batteryPct <= 5 && !isCharging -> ThrottleLevel.CRITICAL
            batteryPct <= 15 && !isCharging -> ThrottleLevel.SEVERE
            batteryPct <= 30 && !isCharging -> ThrottleLevel.MODERATE
            else -> ThrottleLevel.NORMAL
        }
    }

    fun getRecommendedGpsIntervalMs(): Long {
        return when (_powerState.value.throttleLevel) {
            ThrottleLevel.NORMAL -> 5_000L    // 5 seconds
            ThrottleLevel.MODERATE -> 15_000L // 15 seconds
            ThrottleLevel.SEVERE -> 30_000L   // 30 seconds
            ThrottleLevel.CRITICAL -> 60_000L // 60 seconds (Deep power saving)
        }
    }

    fun isAudioStreamingAllowed(): Boolean {
        return _powerState.value.throttleLevel != ThrottleLevel.CRITICAL &&
                _powerState.value.throttleLevel != ThrottleLevel.SEVERE
    }
}
```

---

## 4. Integration into GPS & Audio Pipelines

### In `FusedLocationProviderImpl.kt`:
```kotlin
@Inject lateinit var powerThrottleManager: PowerThrottleManager

// Observe throttle level changes:
coroutineScope.launch {
    powerThrottleManager.powerState.collect { state ->
        val intervalMs = powerThrottleManager.getRecommendedGpsIntervalMs()
        Log.i(TAG, "Adjusting GPS update interval to ${intervalMs}ms based on ${state.throttleLevel}")
        restartLocationUpdates(intervalMs)
    }
}
```

### In `AudioStreamer.kt`:
```kotlin
@Inject lateinit var powerThrottleManager: PowerThrottleManager

fun checkCanStream(): Boolean {
    if (!powerThrottleManager.isAudioStreamingAllowed()) {
        Log.w(TAG, "Audio streaming suppressed due to thermal/battery throttle")
        return false
    }
    return true
}
```
