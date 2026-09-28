package com.sentinel.host.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.sentinel.host.domain.model.LocationUpdate
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
        private const val DEFAULT_STRIDE_LENGTH_METERS = 0.75
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _estimatedLocation = MutableStateFlow<LocationUpdate?>(null)
    val estimatedLocation: StateFlow<LocationUpdate?> = _estimatedLocation.asStateFlow()

    @Volatile
    private var lastKnownGpsLocation: LocationUpdate? = null

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

    fun onGpsLocationReceived(location: LocationUpdate) {
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

        val extrapolated = LocationUpdate(
            latitude = newLat,
            longitude = newLon,
            accuracy = estimatedErrorMeters,
            battery = currentLoc.battery,
            network = "${currentLoc.network} (DeadReckoning)"
        )

        _estimatedLocation.value = extrapolated
        Log.d(TAG, "Extrapolated location: $newLat, $newLon (bearing: $currentBearingDegrees°, error: ${estimatedErrorMeters}m)")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
