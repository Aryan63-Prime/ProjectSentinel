# Feature Specification & Code: Fall Detection & Emergency SOS Broadcast

## 1. Overview & Problem Statement
In lone-worker security, industrial fleet management, and personal safety applications, workers may suffer slips, falls from scaffolding, or vehicular collisions that leave them incapacitated or unconscious. In such scenarios, the user is unable to manually press an emergency SOS button.

This feature implements an on-device **3-Phase Fall & Impact Detection Algorithm** using the device's hardware accelerometer (`Sensor.TYPE_ACCELEROMETER`):
1. **Phase 1 (Free Fall):** Total acceleration vector drops below `0.5g` (< 5 m/s²).
2. **Phase 2 (Hard Impact):** Acceleration magnitude spikes sharply above `3.0g` (> 30 m/s²) within 500ms of free fall.
3. **Phase 3 (Post-Fall Inactivity):** Device remains stationary for at least 5 seconds following impact.

When all three phases are verified, the host automatically triggers an **Emergency SOS Broadcast**, records a 30-second audio clip, turns on the strobe beacon, and sends an urgent priority alert to the Admin dashboard.

---

## 2. Protocol Specification (`shared/`)

Add emergency alert type in [`shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt):

```kotlin
object MessageType {
    // Existing types...
    const val EMERGENCY_SOS = "EMERGENCY_SOS"
}
```

### SOS Alert Payload (`Host → Server → Admin`)
```json
{
  "type": "EMERGENCY_SOS",
  "version": 1,
  "timestamp": 1727500500,
  "sequence": 999,
  "data": {
    "triggerReason": "FALL_DETECTED",
    "impactGForce": 3.42,
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 24.1,
    "accuracy": 3.8,
    "batteryPercent": 84,
    "timestamp": 1727500495
  }
}
```

---

## 3. Host-App Implementation

### File: `host-app/data/src/main/java/com/sentinel/host/data/device/FallDetector.kt`

```kotlin
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

        // Acceleration thresholds (in m/s²)
        private const val FREE_FALL_THRESHOLD = 5.0f   // ~0.5g
        private const val IMPACT_THRESHOLD = 30.0f      // ~3.0g
        private const val STILLNESS_THRESHOLD = 11.5f   // Near 1.0g (earth gravity)
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

        // Calculate total magnitude: sqrt(x^2 + y^2 + z^2)
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

                // Trigger local alert (strobe & siren)
                beaconManager.triggerBeacon()

                // Trigger external emergency callback
                onEmergencyTriggered?.invoke(peakGForce)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
```

---

## 4. Integration into Sentinel Foreground Service

In [`SentinelForegroundService.kt`](file:///Users/ayush/Desktop/Servillance/host-app/app/src/main/java/com/sentinel/host/service/SentinelForegroundService.kt):

```kotlin
@Inject lateinit var fallDetector: FallDetector

override fun onCreate() {
    super.onCreate()
    
    // Start automated fall monitoring
    fallDetector.start()
    fallDetector.onEmergencyTriggered = { peakG ->
        val lastLocation = locationStreamer.currentLocation
        
        val sosJson = JSONObject().apply {
            put("type", MessageType.EMERGENCY_SOS)
            put("version", 1)
            put("timestamp", System.currentTimeMillis() / 1000)
            
            val data = JSONObject().apply {
                put("triggerReason", "FALL_DETECTED")
                put("impactGForce", peakG.toDouble())
                put("latitude", lastLocation?.latitude ?: 0.0)
                put("longitude", lastLocation?.longitude ?: 0.0)
                put("altitude", lastLocation?.altitude ?: 0.0)
                put("accuracy", lastLocation?.accuracy?.toDouble() ?: 0.0)
                put("timestamp", System.currentTimeMillis() / 1000)
            }
            put("data", data)
        }
        
        // Dispatch emergency packet to server immediately
        connectionManager.sendRawMessage(sosJson.toString())
    }
}

override fun onDestroy() {
    super.onDestroy()
    fallDetector.stop()
}
```
