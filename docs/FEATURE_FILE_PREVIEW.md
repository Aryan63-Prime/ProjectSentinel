# Feature Specification & Code: Remote In-Memory File Preview

## 1. Overview & Problem Statement
Streaming multi-megabyte files (such as 25 MB camera photos, large video recordings, or multi-gigabyte log files) across mobile networks over WebSockets creates severe network latency, spikes memory usage, and risks `OutOfMemoryError` (OOM) on both host and admin devices.

This feature enables **in-memory downsampled thumbnails and text snippets** generated on the fly on the host device. Previews are encoded directly into Base64 within RAM (target payload: **30 KB – 80 KB**) and transmitted over WebSocket with **zero disk writes** on either the host or the server.

---

## 2. Protocol Specification (`shared/`)

Add the command type to [`shared/src/main/java/com/sentinel/shared/protocol/CommandTypes.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/CommandTypes.kt):

```kotlin
package com.sentinel.shared.protocol

object CommandTypes {
    // Existing commands...
    const val PREVIEW_FILE = "PREVIEW_FILE"
}
```

### Request Payload (`Admin → Server → Host`)
```json
{
  "type": "COMMAND",
  "version": 1,
  "timestamp": 1727500000,
  "sequence": 101,
  "data": {
    "command": "PREVIEW_FILE",
    "params": {
      "path": "/storage/emulated/0/DCIM/Camera/IMG_2026.jpg",
      "maxDim": 720,
      "textLines": 250
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
  "sequence": 101,
  "data": {
    "command": "PREVIEW_FILE",
    "success": true,
    "payload": {
      "previewType": "IMAGE",
      "mimeType": "image/jpeg",
      "previewBase64": "/9j/4AAQSkZJRgABAQAAAQ...",
      "path": "/storage/emulated/0/DCIM/Camera/IMG_2026.jpg",
      "fileName": "IMG_2026.jpg",
      "fileSizeBytes": 15420312,
      "lastModified": 1727498000,
      "truncated": false
    }
  }
}
```

---

## 3. Host-App Implementation

### File: `host-app/data/src/main/java/com/sentinel/host/data/device/FilePreviewManager.kt`

```kotlin
package com.sentinel.host.data.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilePreviewManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:FilePreview"
        private const val DEFAULT_MAX_DIM = 720
        private const val MAX_TEXT_BYTES = 32 * 1024 // 32 KB preview
    }

    suspend fun generatePreview(
        filePath: String,
        maxDim: Int = DEFAULT_MAX_DIM,
        textLinesLimit: Int = 250
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.isDirectory) {
            return@withContext mapOf(
                "success" to false,
                "error" to "File does not exist or cannot be read: $filePath"
            )
        }

        val ext = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

        try {
            when {
                // 1. Image Files
                isImage(ext, mimeType) -> generateImagePreview(file, maxDim)

                // 2. Video Files (Keyframe extraction)
                isVideo(ext, mimeType) -> generateVideoThumbnail(file, maxDim)

                // 3. PDF Files (First page rasterization)
                ext == "pdf" -> generatePdfPreview(file, maxDim)

                // 4. Text / Logs / Source Code
                isText(ext, mimeType) -> generateTextPreview(file, textLinesLimit)

                // 5. Binary / Other: Metadata only
                else -> mapOf(
                    "previewType" to "METADATA_ONLY",
                    "mimeType" to mimeType,
                    "previewBase64" to "",
                    "path" to file.absolutePath,
                    "fileName" to file.name,
                    "fileSizeBytes" to file.length(),
                    "lastModified" to file.lastModified(),
                    "truncated" to false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate preview for ${file.name}: ${e.message}", e)
            mapOf(
                "success" to false,
                "error" to "Preview generation failed: ${e.localizedMessage}"
            )
        }
    }

    private fun isImage(ext: String, mime: String) =
        mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "heic", "bmp")

    private fun isVideo(ext: String, mime: String) =
        mime.startsWith("video/") || ext in listOf("mp4", "mkv", "3gp", "webm", "avi", "mov")

    private fun isText(ext: String, mime: String) =
        mime.startsWith("text/") || ext in listOf("txt", "log", "json", "xml", "kt", "java", "py", "sh", "properties", "csv", "md")

    private fun generateImagePreview(file: File, maxDim: Int): Map<String, Any?> {
        // Step 1: Decode bounds only
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight

        // Step 2: Compute power-of-two inSampleSize
        var inSampleSize = 1
        while ((origWidth / inSampleSize) > maxDim || (origHeight / inSampleSize) > maxDim) {
            inSampleSize *= 2
        }

        // Step 3: Decode scaled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // 16-bit to halve RAM usage
        }

        val scaledBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Could not decode image bitmap")

        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        scaledBitmap.recycle()

        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        return mapOf(
            "previewType" to "IMAGE",
            "mimeType" to "image/jpeg",
            "previewBase64" to base64,
            "path" to file.absolutePath,
            "fileName" to file.name,
            "fileSizeBytes" to file.length(),
            "lastModified" to file.lastModified(),
            "truncated" to false
        )
    }

    private fun generateVideoThumbnail(file: File, maxDim: Int): Map<String, Any?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IllegalStateException("Unable to retrieve video frame")

            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val targetW = if (aspect >= 1f) maxDim else (maxDim * aspect).toInt()
            val targetH = if (aspect >= 1f) (maxDim / aspect).toInt() else maxDim

            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            bitmap.recycle()

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            scaled.recycle()

            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            mapOf(
                "previewType" to "VIDEO_THUMBNAIL",
                "mimeType" to "image/jpeg",
                "previewBase64" to base64,
                "path" to file.absolutePath,
                "fileName" to file.name,
                "fileSizeBytes" to file.length(),
                "lastModified" to file.lastModified(),
                "truncated" to false
            )
        } finally {
            retriever.release()
        }
    }

    private fun generatePdfPreview(file: File, maxDim: Int): Map<String, Any?> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        return try {
            if (renderer.pageCount == 0) throw IllegalStateException("PDF has no pages")
            val page = renderer.openPage(0)

            val aspect = page.width.toFloat() / page.height.toFloat()
            val targetW = if (aspect >= 1f) maxDim else (maxDim * aspect).toInt()
            val targetH = if (aspect >= 1f) (maxDim / aspect).toInt() else maxDim

            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            bitmap.recycle()

            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            mapOf(
                "previewType" to "PDF",
                "mimeType" to "image/jpeg",
                "previewBase64" to base64,
                "path" to file.absolutePath,
                "fileName" to file.name,
                "fileSizeBytes" to file.length(),
                "lastModified" to file.lastModified(),
                "pageCount" to renderer.pageCount,
                "truncated" to false
            )
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    private fun generateTextPreview(file: File, maxLines: Int): Map<String, Any?> {
        val fis = FileInputStream(file)
        val buffer = ByteArray(MAX_TEXT_BYTES)
        val bytesRead = fis.read(buffer)
        fis.close()

        val textContent = if (bytesRead > 0) String(buffer, 0, bytesRead, Charsets.UTF_8) else ""
        val lines = textContent.lines()
        val truncatedByLines = lines.size > maxLines
        val previewSnippet = lines.take(maxLines).joinToString("\n")
        val isTruncated = truncatedByLines || file.length() > MAX_TEXT_BYTES

        return mapOf(
            "previewType" to "TEXT",
            "mimeType" to "text/plain",
            "textContent" to previewSnippet,
            "path" to file.absolutePath,
            "fileName" to file.name,
            "fileSizeBytes" to file.length(),
            "lastModified" to file.lastModified(),
            "truncated" to isTruncated
        )
    }
}
```

### Wiring into `CommandProcessor.kt`

In [`CommandProcessor.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/CommandProcessor.kt):

```kotlin
// Add FilePreviewManager injection
@Inject lateinit var filePreviewManager: FilePreviewManager

// Inside when (command):
CommandTypes.PREVIEW_FILE -> {
    val path = params.optString("path", "")
    val maxDim = params.optInt("maxDim", 720)
    val textLines = params.optInt("textLines", 250)
    val previewResult = filePreviewManager.generatePreview(path, maxDim, textLines)
    resultPayload.putAll(previewResult)
}
```

---

## 4. Admin-App Implementation

### File: `admin-app/app/src/main/java/com/sentinel/admin/ui/detail/FilePreviewDialog.kt`

```kotlin
package com.sentinel.admin.ui.detail

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun FilePreviewDialog(
    payload: Map<String, Any?>,
    onDismiss: () -> Unit,
    onDownloadFullFile: (path: String) -> Unit
) {
    val previewType = payload["previewType"] as? String ?: "METADATA_ONLY"
    val fileName = payload["fileName"] as? String ?: "File Preview"
    val filePath = payload["path"] as? String ?: ""
    val fileSizeBytes = (payload["fileSizeBytes"] as? Number)?.toLong() ?: 0L
    val previewBase64 = payload["previewBase64"] as? String ?: ""
    val textContent = payload["textContent"] as? String ?: ""
    val isTruncated = payload["truncated"] as? Boolean ?: false

    val bitmap = remember(previewBase64) {
        if (previewBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(previewBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = String.format("%.2f MB", fileSizeBytes / (1024.0 * 1024.0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content Preview Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        bitmap != null -> {
                            Image(
                                bitmap = bitmap,
                                contentDescription = fileName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        previewType == "TEXT" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = textContent,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (isTruncated) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "--- [Preview truncated, download for complete content] ---",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = "Preview not available for this file type.\nDownload to view full content.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onDownloadFullFile(filePath) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}
```
