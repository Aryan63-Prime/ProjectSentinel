# Feature Specification & Code: Inertial Navigation & Dead Reckoning (Sensor Fusion)

## 1. Overview & Problem Statement
When a mobile device enters an underground parking structure, tunnel, basement, or urban canyon surrounded by skyscrapers, satellite GPS signals drop completely (`onProviderDisabled` or satellite fix timeout). Standard location providers freeze at the last known coordinate until open sky is reached.

This feature implements an on-device **Dead Reckoning / Inertial Navigation System (INS)** using Android hardware sensors (`Sensor.TYPE_ACCELEROMETER`, `Sensor.TYPE_GYROSCOPE`, and `Sensor.TYPE_ROTATION_VECTOR` or `Sensor.TYPE_STEP_DETECTOR`). When GPS satellite lock is lost, the engine calculates heading changes and step displacement to extrapolate forward coordinates in real time.

---

## 2. Protocol Specification (`shared/`)

When transmitting dead-reckoning coordinates, the packet explicitly indicates the calculated confidence radius:

```json
{
  "type": "LOCATION",
  "version": 1,
  "timestamp": 1727500030,
  "sequence": 501,
  "data": {
    "latitude": 37.77542,
    "longitude": -122.41981,
    "altitude": 14.8,
    "accuracy": 15.0,
    "speed": 1.35,
    "bearing": 210.5,
    "timestamp": 1727500030,
    "provider": "DEAD_RECKONING",
    "isDeadReckoning": true
  }
}
```

---

## 3. Host-App Implementation

### File: `host-app/data/src/main/java/com/sentinel/host/data/location/DeadReckoningEngine.kt`

```kotlin
package com.sentinel.host.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.sentinel.shared.model.LocationData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class DeadReckoningEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    companion object {
        private const val TAG = "Sentinel:DeadReckoning"
        private const val EARTH_RADIUS_METERS = 6378137.0
        private const val DEFAULT_STRIDE_LENGTH_METERS = 0.75 // Average adult walking stride
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _estimatedLocation = MutableStateFlow<LocationData?>(null)
    val estimatedLocation: StateFlow<LocationData?> = _estimatedLocation.asStateFlow()

    @Volatile
    private var lastKnownGpsLocation: LocationData? = null

    @Volatile
    private var currentBearingDegrees: Float = 0f

    @Volatile
    private var isGpsLost: Boolean = false

    private var deadReckoningDistanceMeters = 0.0
    private var estimatedErrorMeters = 5f

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        Log.i(TAG, "Dead reckoning sensors registered")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i(TAG, "Dead reckoning sensors unregistered")
    }

    fun onGpsLocationReceived(location: LocationData) {
        lastKnownGpsLocation = location
        isGpsLost = false
        deadReckoningDistanceMeters = 0.0
        estimatedErrorMeters = location.accuracy
    }

    fun onGpsSignalLost() {
        if (!isGpsLost) {
            isGpsLost = true
            Log.w(TAG, "GPS fix lost! Activating inertial dead reckoning extrapolation")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)

                // Azimuth (heading in radians) -> Convert to 0-360 degrees
                val azimuthRad = orientation[0]
                var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                if (azimuthDeg < 0) azimuthDeg += 360f
                currentBearingDegrees = azimuthDeg
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                if (isGpsLost && lastKnownGpsLocation != null) {
                    extrapolatePosition(DEFAULT_STRIDE_LENGTH_METERS)
                }
            }
        }
    }

    private fun extrapolatePosition(distanceMeters: Double) {
        val currentLoc = _estimatedLocation.value ?: lastKnownGpsLocation ?: return

        deadReckoningDistanceMeters += distanceMeters
        // Drift error increases by roughly 5% of distance traveled without GPS fix
        estimatedErrorMeters = (estimatedErrorMeters + (distanceMeters * 0.05)).toFloat()

        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearingRad = Math.toRadians(currentBearingDegrees.toDouble())
        val lat1Rad = Math.toRadians(currentLoc.latitude)
        val lon1Rad = Math.toRadians(currentLoc.longitude)

        val lat2Rad = asin(
            sin(lat1Rad) * cos(angularDistance) +
                    cos(lat1Rad) * sin(angularDistance) * cos(bearingRad)
        )

        val lon2Rad = lon1Rad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(lat1Rad),
            cos(angularDistance) - sin(lat1Rad) * sin(lat2Rad)
        )

        val newLat = Math.toDegrees(lat2Rad)
        val newLon = Math.toDegrees(lon2Rad)

        val extrapolated = LocationData(
            latitude = newLat,
            longitude = newLon,
            altitude = currentLoc.altitude,
            accuracy = estimatedErrorMeters,
            speed = 1.3f, // Typical walking speed ~1.3 m/s
            bearing = currentBearingDegrees,
            timestamp = System.currentTimeMillis() / 1000,
            provider = "DEAD_RECKONING"
        )

        _estimatedLocation.value = extrapolated
        Log.d(TAG, "Extrapolated location: $newLat, $newLon (bearing: $currentBearingDegrees°, error: ${estimatedErrorMeters}m)")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
```

---

## 4. Integration into Location Streamer

In [`LocationStreamer.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/LocationStreamer.kt):

```kotlin
@Inject lateinit var deadReckoningEngine: DeadReckoningEngine

// When GPS emits a fix:
deadReckoningEngine.onGpsLocationReceived(locationData)

// If no GPS update received within 15 seconds:
scope.launch {
    while (isActive) {
        delay(15_000)
        val lastFixAge = System.currentTimeMillis() - lastGpsFixTime
        if (lastFixAge > 15_000) {
            deadReckoningEngine.onGpsSignalLost()
        }
    }
}

// Stream dead-reckoning coordinates when active:
scope.launch {
    deadReckoningEngine.estimatedLocation.collect { estimatedLoc ->
        if (estimatedLoc != null && isGpsLost) {
            sendLocationPacket(estimatedLoc)
        }
    }
}
```
