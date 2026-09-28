package com.sentinel.host.data.location

import android.util.Log
import com.sentinel.host.data.db.LocationDao
import com.sentinel.host.data.db.LocationEntity
import com.sentinel.host.domain.model.LocationUpdate
import com.sentinel.shared.protocol.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineTelemetryBuffer @Inject constructor(
    private val locationDao: LocationDao
) {
    companion object {
        private const val TAG = "Sentinel:OfflineBuffer"
        private const val BATCH_SIZE = 100
    }

    private val flushMutex = Mutex()

    suspend fun bufferLocation(loc: LocationUpdate) = withContext(Dispatchers.IO) {
        val entity = LocationEntity(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy,
            battery = loc.battery,
            network = loc.network,
            timestamp = System.currentTimeMillis() / 1000,
            provider = "fused"
        )
        locationDao.insert(entity)
        locationDao.pruneOldest()
        Log.d(TAG, "Buffered offline location: ${loc.latitude}, ${loc.longitude}")
    }

    suspend fun flushBuffer(sendBatch: (String) -> Boolean): Int = withContext(Dispatchers.IO) {
        flushMutex.withLock {
            var totalFlushed = 0
            while (true) {
                val batch = locationDao.getOldestBatch(BATCH_SIZE)
                if (batch.isEmpty()) break

                val jsonBatch = JSONObject().apply {
                    put("type", MessageType.LOCATION_BATCH)
                    put("version", 1)
                    put("timestamp", System.currentTimeMillis() / 1000)

                    val dataObj = JSONObject()
                    dataObj.put("count", batch.size)

                    val pointsArray = JSONArray()
                    batch.forEach { point ->
                        pointsArray.put(
                            JSONObject().apply {
                                put("latitude", point.latitude)
                                put("longitude", point.longitude)
                                put("altitude", point.altitude)
                                put("accuracy", point.accuracy.toDouble())
                                put("speed", point.speed.toDouble())
                                put("bearing", point.bearing.toDouble())
                                put("battery", point.battery)
                                put("network", point.network)
                                put("timestamp", point.timestamp)
                                put("provider", point.provider)
                            }
                        )
                    }
                    dataObj.put("points", pointsArray)
                    put("data", dataObj)
                }

                val success = sendBatch(jsonBatch.toString())
                if (success) {
                    val ids = batch.map { it.id }
                    locationDao.deleteBatch(ids)
                    totalFlushed += batch.size
                    Log.i(TAG, "Flushed ${batch.size} offline locations to server")
                } else {
                    Log.w(TAG, "Batch flush failed, keeping remaining in DB")
                    break
                }
            }
            totalFlushed
        }
    }
}
