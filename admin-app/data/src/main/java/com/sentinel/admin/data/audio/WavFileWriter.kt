package com.sentinel.admin.data.audio

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streaming RIFF WAV File Writer for 16-bit PCM Mono audio at 48,000 Hz.
 *
 * Header Layout (44 Bytes total):
 * - 0..3:   "RIFF"
 * - 4..7:   ChunkSize (36 + subChunk2Size)
 * - 8..11:  "WAVE"
 * - 12..15: "fmt "
 * - 16..19: Subchunk1Size (16 for PCM)
 * - 20..21: AudioFormat (1 for PCM)
 * - 22..23: NumChannels (1 for Mono)
 * - 24..27: SampleRate (48000)
 * - 28..31: ByteRate (SampleRate * NumChannels * BitsPerSample / 8 = 96000)
 * - 32..33: BlockAlign (NumChannels * BitsPerSample / 8 = 2)
 * - 34..35: BitsPerSample (16)
 * - 36..39: "data"
 * - 40..43: Subchunk2Size (NumSamples * NumChannels * BitsPerSample / 8)
 *
 * Thread Safety:
 * - Thread-safe when calls to [writePcm] and [close] are synchronized.
 */
class WavFileWriter(
    val file: File,
    private val sampleRate: Int = 48000,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16
) {

    companion object {
        private const val TAG = "Sentinel:WavWriter"
        private const val HEADER_SIZE = 44
    }

    private var randomAccessFile: RandomAccessFile? = null
    private var bytesWritten: Long = 0L
    private var isClosed = false

    init {
        try {
            if (file.exists()) {
                file.delete()
            }
            file.parentFile?.mkdirs()

            val raf = RandomAccessFile(file, "rw")
            randomAccessFile = raf

            // Write initial placeholder 44-byte WAV header
            val header = ByteArray(HEADER_SIZE)
            raf.write(header)
            Log.i(TAG, "WAV recording file created: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WAV file writer: ${e.message}", e)
            close()
        }
    }

    /**
     * Appends a buffer of 16-bit PCM shorts to the WAV file.
     */
    @Synchronized
    fun writePcm(shorts: ShortArray, count: Int) {
        if (isClosed) return
        val raf = randomAccessFile ?: return

        try {
            val byteBuffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                byteBuffer.putShort(shorts[i])
            }

            val bytes = byteBuffer.array()
            raf.write(bytes)
            bytesWritten += bytes.size
        } catch (e: Exception) {
            Log.e(TAG, "Error writing PCM data to WAV file: ${e.message}", e)
        }
    }

    /**
     * Finalizes the WAV header with total file sizes and closes the file handle.
     */
    @Synchronized
    fun close(): File? {
        if (isClosed) return if (file.exists()) file else null
        isClosed = true

        val raf = randomAccessFile
        if (raf != null) {
            try {
                // Return to header start to write valid sizes
                raf.seek(0)

                val byteRate = sampleRate * channels * bitsPerSample / 8
                val blockAlign = (channels * bitsPerSample / 8).toShort()
                val totalDataLen = bytesWritten
                val totalFileLen = totalDataLen + 36

                val headerBuffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

                // 0..3: RIFF
                headerBuffer.put('R'.code.toByte())
                headerBuffer.put('I'.code.toByte())
                headerBuffer.put('F'.code.toByte())
                headerBuffer.put('F'.code.toByte())

                // 4..7: Total file size - 8
                headerBuffer.putInt(totalFileLen.toInt())

                // 8..11: WAVE
                headerBuffer.put('W'.code.toByte())
                headerBuffer.put('A'.code.toByte())
                headerBuffer.put('V'.code.toByte())
                headerBuffer.put('E'.code.toByte())

                // 12..15: fmt
                headerBuffer.put('f'.code.toByte())
                headerBuffer.put('m'.code.toByte())
                headerBuffer.put('t'.code.toByte())
                headerBuffer.put(' '.code.toByte())

                // 16..19: Subchunk1Size (16 for PCM)
                headerBuffer.putInt(16)

                // 20..21: AudioFormat (1 for PCM)
                headerBuffer.putShort(1.toShort())

                // 22..23: NumChannels
                headerBuffer.putShort(channels.toShort())

                // 24..27: SampleRate
                headerBuffer.putInt(sampleRate)

                // 28..31: ByteRate
                headerBuffer.putInt(byteRate)

                // 32..33: BlockAlign
                headerBuffer.putShort(blockAlign)

                // 34..35: BitsPerSample
                headerBuffer.putShort(bitsPerSample.toShort())

                // 36..39: data
                headerBuffer.put('d'.code.toByte())
                headerBuffer.put('a'.code.toByte())
                headerBuffer.put('t'.code.toByte())
                headerBuffer.put('a'.code.toByte())

                // 40..43: Subchunk2Size (bytesWritten)
                headerBuffer.putInt(totalDataLen.toInt())

                raf.write(headerBuffer.array())
                raf.close()
                Log.i(TAG, "WAV header finalized (bytesWritten=$bytesWritten, path=${file.absolutePath})")
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing WAV header: ${e.message}", e)
            } finally {
                randomAccessFile = null
            }
        }

        return if (file.exists() && bytesWritten > 0) file else null
    }
}
