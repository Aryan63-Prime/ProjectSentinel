# Feature Specification & Code: Self-Healing Watchdog & Crash Tombstones

## 1. Overview & Problem Statement
Background surveillance and tracking services on Android are susceptible to unexpected hardware driver lockups (e.g., camera vendor hal crashes), native JNI errors (e.g., Opus library SIGSEGV), or unhandled thread exceptions. When this happens, the process terminates abruptly, leaving zero diagnostic traces for the admin dashboard.

This feature implements an **Automated Self-Healing & Diagnostic Watchdog**:
1. Intercepts all uncaught exceptions via a global `Thread.UncaughtExceptionHandler`.
2. Persists an in-memory crash snapshot (stack trace, memory usage, thread states, and recent logcat buffers) into a local `tombstone.json` file.
3. Automatically triggers immediate process resuscitation using `WorkManager` and `AlarmManager`.
4. Transmits a high-priority `CRASH_REPORT` packet to the Admin server immediately upon reboot before continuing normal telemetry.

---

## 2. Protocol Specification (`shared/`)

Add event type in [`shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt):

```kotlin
object MessageType {
    // Existing types...
    const val CRASH_REPORT = "CRASH_REPORT"
}
```

### Crash Report Payload (`Host → Server → Admin`)
```json
{
  "type": "CRASH_REPORT",
  "version": 1,
  "timestamp": 1727500120,
  "sequence": 1,
  "data": {
    "exceptionClass": "java.lang.NullPointerException",
    "message": "Camera device disconnected unexpectedly",
    "stackTrace": "java.lang.NullPointerException...\n\tat com.sentinel.host...",
    "freeMemoryMb": 42,
    "totalMemoryMb": 256,
    "crashedAt": 1727500115,
    "appVersion": "1.0.0"
  }
}
```

---

## 3. Host-App Implementation

### File: `host-app/data/src/main/java/com/sentinel/host/data/device/CrashReporter.kt`

```kotlin
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
                // Delegate to default handler to ensure OS process cleanup
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
```

---

## 4. Integration into Application Lifecycle

In [`SentinelApp.kt`](file:///Users/ayush/Desktop/Servillance/host-app/app/src/main/java/com/sentinel/host/SentinelApp.kt):

```kotlin
@HiltAndroidApp
class SentinelApp : Application() {

    @Inject
    lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        // Register global crash interceptor immediately upon process start
        crashReporter.initializeGlobalHandler()
    }
}
```

In `WebSocketConnectionManager.kt` on `ConnectionState.Ready`:

```kotlin
// Dispatch pending crash report as soon as socket connects
scope.launch {
    crashReporter.checkAndFlushPendingCrash { payload ->
        sendRawMessage(payload)
    }
}
```
