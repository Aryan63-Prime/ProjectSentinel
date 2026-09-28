package com.sentinel.host.data.device

import android.content.Context
import android.util.Log
import com.sentinel.shared.protocol.MessageType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:CrashReporter"
        private const val TOMBSTONE_FILE = "tombstone.json"
    }

    private val tombstoneFile = File(context.filesDir, TOMBSTONE_FILE)

    fun initializeGlobalHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record crash: ${e.message}", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        Log.i(TAG, "Global crash handler initialized")
    }

    private fun recordCrash(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTraceStr = sw.toString()

        val runtime = Runtime.getRuntime()
        val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
        val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)

        val crashJson = JSONObject().apply {
            put("type", MessageType.CRASH_REPORT)
            put("version", 1)
            put("timestamp", System.currentTimeMillis() / 1000)

            val data = JSONObject().apply {
                put("threadName", thread.name)
                put("exceptionClass", throwable.javaClass.name)
                put("message", throwable.message ?: "No error message")
                put("stackTrace", stackTraceStr)
                put("freeMemoryMb", freeMemoryMb)
                put("totalMemoryMb", totalMemoryMb)
                put("crashedAt", System.currentTimeMillis() / 1000)
            }
            put("data", data)
        }

        tombstoneFile.writeText(crashJson.toString())
        Log.e(TAG, "Tombstone written for ${throwable.javaClass.simpleName}")
    }

    suspend fun checkAndFlushPendingCrash(sendPayload: (String) -> Boolean) = withContext(Dispatchers.IO) {
        if (!tombstoneFile.exists()) return@withContext

        try {
            val content = tombstoneFile.readText()
            if (content.isNotBlank()) {
                val sent = sendPayload(content)
                if (sent) {
                    tombstoneFile.delete()
                    Log.i(TAG, "Previous crash report dispatched to server and deleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing crash report: ${e.message}", e)
        }
    }
}
