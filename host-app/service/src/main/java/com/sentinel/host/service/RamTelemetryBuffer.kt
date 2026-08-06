package com.sentinel.host.service

import android.util.Log
import com.sentinel.host.domain.model.LocationUpdate
import java.util.ArrayDeque

/**
 * Enterprise In-Memory Transient Ring Buffer.
 * Zero-Disk Footprint: Preserves up to 20 transient location fixes in RAM during brief network handoffs.
 * Automatically evicts oldest fixes if capacity is reached. 0 bytes written to disk.
 */
class RamTelemetryBuffer(private val maxCapacity: Int = 20) {

    companion object {
        private const val TAG = "Sentinel:RamBuffer"
    }

    private val queue = ArrayDeque<LocationUpdate>()

    @Synchronized
    fun offer(locationUpdate: LocationUpdate) {
        if (queue.size >= maxCapacity) {
            val evicted = queue.pollFirst()
            Log.d(TAG, "RAM Ring Buffer at capacity ($maxCapacity). Evicted oldest fix: ${evicted?.latitude}, ${evicted?.longitude}")
        }
        queue.addLast(locationUpdate)
        Log.d(TAG, "Buffered location fix in RAM. Current size: ${queue.size}/$maxCapacity")
    }

    @Synchronized
    fun drainAll(): List<LocationUpdate> {
        if (queue.isEmpty()) return emptyList()
        val batch = ArrayList<LocationUpdate>(queue)
        queue.clear()
        Log.i(TAG, "Drained ${batch.size} buffered location fixes from RAM")
        return batch
    }

    @Synchronized
    fun clear() {
        queue.clear()
    }

    val size: Int
        @Synchronized get() = queue.size
}
