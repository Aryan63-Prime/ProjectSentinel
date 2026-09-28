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
            try {
                powerManager.addThermalStatusListener { status ->
                    handleThermalChange(status)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ThermalStatusListener registration failed: ${e.message}")
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

        return when {
            batteryPct <= 5 && !isCharging -> ThrottleLevel.CRITICAL
            batteryPct <= 15 && !isCharging -> ThrottleLevel.SEVERE
            batteryPct <= 30 && !isCharging -> ThrottleLevel.MODERATE
            else -> ThrottleLevel.NORMAL
        }
    }

    fun getRecommendedGpsIntervalMs(): Long {
        return when (_powerState.value.throttleLevel) {
            ThrottleLevel.NORMAL -> 5_000L
            ThrottleLevel.MODERATE -> 15_000L
            ThrottleLevel.SEVERE -> 30_000L
            ThrottleLevel.CRITICAL -> 60_000L
        }
    }

    fun isAudioStreamingAllowed(): Boolean {
        return _powerState.value.throttleLevel != ThrottleLevel.CRITICAL &&
                _powerState.value.throttleLevel != ThrottleLevel.SEVERE
    }
}
