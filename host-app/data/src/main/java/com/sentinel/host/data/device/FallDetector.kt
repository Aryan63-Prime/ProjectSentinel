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
        private const val FREE_FALL_THRESHOLD = 5.0f   // ~0.5g
        private const val IMPACT_THRESHOLD = 30.0f      // ~3.0g
        private const val IMPACT_WINDOW_MS = 600L
        private const val STILLNESS_DURATION_MS = 5000L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val scope = CoroutineScope(Dispatchers.Default)

    private var freeFallTimestamp: Long = 0L
    private var impactDetected = false
    private var peakGForce = 0f

    var onEmergencyTriggered: ((impactGForce: Float) -> Unit)? = null

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.i(TAG, "Fall detector accelerometer registered")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i(TAG, "Fall detector accelerometer unregistered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val now = System.currentTimeMillis()

        // Phase 1: Free Fall Detection
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
        scope.launch {
            delay(STILLNESS_DURATION_MS)

            if (impactDetected) {
                Log.e(TAG, "EMERGENCY: Worker fall and sustained inactivity verified! Triggering SOS.")
                impactDetected = false

                // Trigger local alert (strobe & tone)
                beaconManager.triggerBeacon()

                // Trigger external emergency callback
                onEmergencyTriggered?.invoke(peakGForce)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
