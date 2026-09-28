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
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight

        var inSampleSize = 1
        while ((origWidth / inSampleSize) > maxDim || (origHeight / inSampleSize) > maxDim) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
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
