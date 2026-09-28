package com.sentinel.host.data.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class FallDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val beaconManager: BeaconManager
) : SensorEventListener {

    companion object {
        private const val TAG = "Sentinel:FallDetector"
        private const val FREE_FALL_THRESHOLD = 2.0f   // ~0.2g (true free fall)
        private const val IMPACT_THRESHOLD = 45.0f      // ~4.5g (severe impact)
        private const val IMPACT_WINDOW_MS = 500L
        private const val STILLNESS_DURATION_MS = 6000L
        private const val MOTION_CANCEL_HIGH = 14.0f
        private const val MOTION_CANCEL_LOW = 6.0f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Master toggle for fall detection. Disabled by default to prevent false alarms. */
    var isEnabled = false
        private set

    /** Controls whether local strobe and siren fire on emergency. Disabled by default. */
    var soundLocalAlarm = false

    private var freeFallTimestamp: Long = 0L
    private var impactDetected = false
    private var peakGForce = 0f
    private var stillnessJob: kotlinx.coroutines.Job? = null

    var onEmergencyTriggered: ((impactGForce: Float) -> Unit)? = null

    fun enable(soundAlarm: Boolean = false) {
        isEnabled = true
        soundLocalAlarm = soundAlarm
        start()
    }

    fun disable() {
        isEnabled = false
        stop()
    }

    fun start() {
        if (!isEnabled) {
            Log.d(TAG, "Fall detector is disabled; skipping sensor registration")
            return
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.i(TAG, "Fall detector accelerometer registered (soundAlarm=$soundLocalAlarm)")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        stillnessJob?.cancel()
        stillnessJob = null
        impactDetected = false
        freeFallTimestamp = 0L
        Log.i(TAG, "Fall detector accelerometer unregistered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEnabled || event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val now = System.currentTimeMillis()

        // If currently in stillness verification, any significant motion cancels the alert
        if (stillnessJob?.isActive == true) {
            if (magnitude > MOTION_CANCEL_HIGH || magnitude < MOTION_CANCEL_LOW) {
                Log.d(TAG, "Active motion detected ($magnitude m/s²), cancelling fall verification")
                stillnessJob?.cancel()
                stillnessJob = null
                impactDetected = false
                return
            }
        }

        // Phase 1: Free Fall Detection (low gravity)
        if (magnitude < FREE_FALL_THRESHOLD) {
            freeFallTimestamp = now
            Log.d(TAG, "Free fall detected: magnitude=$magnitude")
            return
        }

        // Phase 2: Impact Detection following Free Fall
        if (freeFallTimestamp > 0 && (now - freeFallTimestamp) < IMPACT_WINDOW_MS) {
            if (magnitude > IMPACT_THRESHOLD) {
                impactDetected = true
                peakGForce = magnitude / 9.80665f
                freeFallTimestamp = 0L
                Log.w(TAG, "Hard impact confirmed! Peak g-force: ${peakGForce}g. Checking for post-impact stillness...")

                // Phase 3: Post-Impact Stillness Verification
                verifyPostImpactStillness()
            }
        }
    }

    private fun verifyPostImpactStillness() {
        stillnessJob?.cancel()
        stillnessJob = scope.launch {
            delay(STILLNESS_DURATION_MS)

            if (impactDetected) {
                Log.e(TAG, "EMERGENCY: Sustained inactivity after impact verified! Triggering SOS.")
                impactDetected = false

                // Only trigger loud strobe & siren if explicitly configured
                if (soundLocalAlarm) {
                    beaconManager.triggerBeacon()
                }

                // Trigger external emergency callback
                onEmergencyTriggered?.invoke(peakGForce)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
