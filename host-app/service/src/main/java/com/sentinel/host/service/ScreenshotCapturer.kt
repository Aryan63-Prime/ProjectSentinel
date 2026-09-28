package com.sentinel.host.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.sentinel.host.data.device.ShellExecutor
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
