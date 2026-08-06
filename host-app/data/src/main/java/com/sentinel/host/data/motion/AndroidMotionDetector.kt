package com.sentinel.host.data.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.util.Log
import com.sentinel.host.domain.model.MotionState
import com.sentinel.host.domain.motion.MotionDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Enterprise hardware-assisted motion detector implementation.
 * Zero-disk storage: Operates purely in-memory using low-power hardware coprocessor sensor registers.
 */
class AndroidMotionDetector(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : MotionDetector, SensorEventListener {

    companion object {
        private const val TAG = "Sentinel:MotionDetect"
        private const val GRAVITY_EARTH = 9.80665f
        private const val MOTION_VARIANCE_THRESHOLD = 0.45f
        const val STATIONARY_TIMEOUT_MS = 45_000L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sigMotionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private val _motionState = MutableStateFlow(MotionState.IN_MOTION)
    override val motionState: StateFlow<MotionState> = _motionState.asStateFlow()

    private var lastMotionTimeMs: Long = System.currentTimeMillis()
    private var timerJob: Job? = null
    @Volatile private var isRunning = false

    private val sigMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            Log.i(TAG, "Hardware Significant Motion Trigger fired!")
            onMotionDetected()
            // Re-arm significant motion trigger
            armSignificantMotion()
        }
    }

    override fun start() {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "Starting hardware motion detector")

        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        armSignificantMotion()
        startStationaryCheckTimer()
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false
        Log.i(TAG, "Stopping hardware motion detector")

        timerJob?.cancel()
        timerJob = null
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(x * x + y * y + z * z)
        val deltaFromGravity = abs(magnitude - GRAVITY_EARTH)

        if (deltaFromGravity > MOTION_VARIANCE_THRESHOLD) {
            onMotionDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onMotionDetected() {
        lastMotionTimeMs = System.currentTimeMillis()
        if (_motionState.value != MotionState.IN_MOTION) {
            Log.i(TAG, "Motion detected — state: STATIONARY → IN_MOTION")
            _motionState.value = MotionState.IN_MOTION
        }
    }

    private fun armSignificantMotion() {
        sigMotionSensor?.let {
            sensorManager?.requestTriggerSensor(sigMotionListener, it)
        }
    }

    private fun startStationaryCheckTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isRunning) {
                delay(10_000L)
                val timeSinceLastMotion = System.currentTimeMillis() - lastMotionTimeMs

                if (timeSinceLastMotion >= STATIONARY_TIMEOUT_MS && _motionState.value != MotionState.STATIONARY) {
                    Log.i(TAG, "Device idle for ${timeSinceLastMotion / 1000}s — state: IN_MOTION → STATIONARY")
                    _motionState.value = MotionState.STATIONARY
                }
            }
        }
    }
}
