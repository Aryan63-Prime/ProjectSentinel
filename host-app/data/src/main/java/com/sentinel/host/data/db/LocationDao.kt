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
