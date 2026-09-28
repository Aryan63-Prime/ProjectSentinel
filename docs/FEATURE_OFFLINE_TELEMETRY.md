# Feature Specification & Code: Offline Telemetry Caching & Batch Synchronization

## 1. Overview & Problem Statement
Mobile devices frequently pass through cellular dead zones (tunnels, elevators, basements, remote roads). Currently, when the WebSocket disconnects, real-time location frames produced by [`LocationStreamer.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/LocationStreamer.kt) are dropped, leaving gaps in historical device tracking.

This feature introduces a **local Room SQLite database buffer** on the host. When the device is offline, coordinates are saved to a local FIFO queue (capped at 5,000 points). Once the WebSocket reconnects and authenticates, the buffer automatically flushes queued coordinates in batch packets (`LOCATION_BATCH`) to the server.

---

## 2. Protocol Specification (`shared/`)

Add batch message type in [`shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt):

```kotlin
object MessageType {
    // Existing types...
    const val LOCATION_BATCH = "LOCATION_BATCH"
}
```

### Packet Payload (`Host → Server`)
```json
{
  "type": "LOCATION_BATCH",
  "version": 1,
  "timestamp": 1727500050,
  "sequence": 450,
  "data": {
    "count": 3,
    "points": [
      {
        "latitude": 37.7749,
        "longitude": -122.4194,
        "altitude": 15.2,
        "accuracy": 4.5,
        "speed": 12.3,
        "bearing": 180.0,
        "timestamp": 1727500010,
        "provider": "fused"
      },
      {
        "latitude": 37.7751,
        "longitude": -122.4196,
        "altitude": 15.4,
        "accuracy": 4.1,
        "speed": 13.0,
        "bearing": 182.0,
        "timestamp": 1727500025,
        "provider": "fused"
      }
    ]
  }
}
```

---

## 3. Host-App Room Database Implementation

### Entity: `host-app/data/src/main/java/com/sentinel/host/data/db/LocationEntity.kt`

```kotlin
package com.sentinel.host.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long,
    val provider: String
)
```

### DAO: `host-app/data/src/main/java/com/sentinel/host/data/db/LocationDao.kt`

```kotlin
package com.sentinel.host.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)

    @Query("SELECT * FROM offline_locations ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getOldestBatch(limit: Int = 100): List<LocationEntity>

    @Query("DELETE FROM offline_locations WHERE id IN (:ids)")
    suspend fun deleteBatch(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM offline_locations")
    suspend fun getCount(): Int

    // FIFO enforcement: Prune oldest rows if buffer exceeds 5,000
    @Query("DELETE FROM offline_locations WHERE id NOT IN (SELECT id FROM offline_locations ORDER BY timestamp DESC LIMIT 5000)")
    suspend fun pruneOldest()
}
```

### Database: `host-app/data/src/main/java/com/sentinel/host/data/db/SentinelDatabase.kt`

```kotlin
package com.sentinel.host.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LocationEntity::class], version = 1, exportSchema = false)
abstract class SentinelDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
}
```

### Buffer Manager: `host-app/data/src/main/java/com/sentinel/host/data/location/OfflineTelemetryBuffer.kt`

```kotlin
package com.sentinel.host.data.location

import android.util.Log
import com.sentinel.host.data.db.LocationDao
import com.sentinel.host.data.db.LocationEntity
import com.sentinel.shared.model.LocationData
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

    suspend fun bufferLocation(loc: LocationData) = withContext(Dispatchers.IO) {
        val entity = LocationEntity(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = loc.altitude,
            accuracy = loc.accuracy,
            speed = loc.speed,
            bearing = loc.bearing,
            timestamp = loc.timestamp,
            provider = loc.provider
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
```

---

## 4. Integration into Connection & Streaming Lifecycle

In [`LocationStreamer.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/LocationStreamer.kt):

```kotlin
// When emitting location:
if (connectionManager.isReady) {
    sendLocationPacket(locationData)
} else {
    // Buffer locally if socket is offline
    offlineBuffer.bufferLocation(locationData)
}
```

In `WebSocketConnectionManager.kt` upon connection transition to `ConnectionState.Ready`:

```kotlin
scope.launch {
    val count = offlineBuffer.flushBuffer { jsonPayload ->
        sendRawMessage(jsonPayload)
    }
    if (count > 0) {
        Log.i(TAG, "Successfully synced $count offline GPS coordinates upon reconnect")
    }
}
```
