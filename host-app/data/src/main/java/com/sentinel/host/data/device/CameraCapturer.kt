package com.sentinel.host.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class CameraCapturer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @SuppressLint("MissingPermission")
    suspend fun capturePhoto(useFrontCamera: Boolean = false): Map<String, Any> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val targetFacing = if (useFrontCamera) {
                    CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    CameraCharacteristics.LENS_FACING_BACK
                }

                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    characteristics.get(CameraCharacteristics.LENS_FACING) == targetFacing
                } ?: cameraManager.cameraIdList.firstOrNull()

                if (cameraId == null) {
                    continuation.resume(mapOf("success" to false, "error" to "No camera available"))
                    return@suspendCancellableCoroutine
                }

                val thread = HandlerThread("CameraBackgroundThread").apply { start() }
                val backgroundHandler = Handler(thread.looper)

                val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()

                        val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        thread.quitSafely()
                        continuation.resume(
                            mapOf(
                                "success" to true,
                                "imageBase64" to base64Image,
                                "cameraFacing" to if (useFrontCamera) "FRONT" else "REAR"
                            )
                        )
                    }
                }, backgroundHandler)

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        try {
                            val surface = imageReader.surface
                            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            captureBuilder.addTarget(surface)

                            camera.createCaptureSession(
                                listOf(surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        try {
                                            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: android.hardware.camera2.TotalCaptureResult
                                                ) {
                                                    camera.close()
                                                }
                                            }, backgroundHandler)
                                        } catch (e: Exception) {
                                            camera.close()
                                            thread.quitSafely()
                                            continuation.resume(mapOf("success" to false, "error" to "Capture failed: ${e.message}"))
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        camera.close()
                                        thread.quitSafely()
                                        continuation.resume(mapOf("success" to false, "error" to "Session configuration failed"))
                                    }
                                },
                                backgroundHandler
                            )
                        } catch (e: Exception) {
                            camera.close()
                            thread.quitSafely()
                            continuation.resume(mapOf("success" to false, "error" to "Camera open failed: ${e.message}"))
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        thread.quitSafely()
                        if (continuation.isActive) {
                            continuation.resume(mapOf("success" to false, "error" to "Camera disconnected"))
                        }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        thread.quitSafely()
                        if (continuation.isActive) {
                            continuation.resume(mapOf("success" to false, "error" to "Camera error code: $error"))
                        }
                    }
                }, backgroundHandler)

            } catch (e: Exception) {
                continuation.resume(mapOf("success" to false, "error" to "Camera exception: ${e.localizedMessage}"))
            }
        }
    }
}
