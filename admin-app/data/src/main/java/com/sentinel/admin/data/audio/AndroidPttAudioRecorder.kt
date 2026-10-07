package com.sentinel.admin.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Android implementation of [PttAudioRecorder] using [AudioRecord].
 *
 * Configured for wideband voice intercom:
 * - Source: [MediaRecorder.AudioSource.VOICE_COMMUNICATION] (activates hardware AEC & noise suppression)
 * - Sample rate: 16000 Hz
 * - Channel: Mono
 * - Encoding: PCM 16-bit
 * - Frame length: 100 ms (1600 samples = 3200 bytes per chunk)
 */
@Singleton
class AndroidPttAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : PttAudioRecorder {

    companion object {
        private const val TAG = "Sentinel:PttRec"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SAMPLES_PER_CHUNK = 1600 // 100ms chunk at 16 kHz = 3200 bytes
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    override var isRecording: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun start(onChunkAvailable: (pcmBase64: String, normalizedLevel: Float) -> Unit): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording, stopping previous session")
            stop()
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid AudioRecord minBufferSize: $minBufferSize")
            return false
        }
        val bufferSize = max(minBufferSize * 2, SAMPLES_PER_CHUNK * 2 * 2)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize (state=${record.state})")
                record.release()
                return false
            }

            record.startRecording()
            audioRecord = record
            isRecording = true
            Log.i(TAG, "PTT voice recording started successfully")

            recordingJob = scope.launch {
                val shortBuffer = ShortArray(SAMPLES_PER_CHUNK)
                val byteBuffer = ByteArray(SAMPLES_PER_CHUNK * 2)

                while (isActive && isRecording) {
                    val readShorts = record.read(shortBuffer, 0, SAMPLES_PER_CHUNK)
                    if (readShorts > 0) {
                        var maxAmp = 0
                        for (i in 0 until readShorts) {
                            val sample = shortBuffer[i]
                            val absSample = abs(sample.toInt())
                            if (absSample > maxAmp) {
                                maxAmp = absSample
                            }
                            // Convert 16-bit little-endian short to byte array
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }

                        val normalizedLevel = (maxAmp / 32767f).coerceIn(0f, 1f)
                        val validBytes = if (readShorts == SAMPLES_PER_CHUNK) {
                            byteBuffer
                        } else {
                            byteBuffer.copyOf(readShorts * 2)
                        }

                        val base64 = Base64.encodeToString(validBytes, Base64.NO_WRAP)
                        onChunkAvailable(base64, normalizedLevel)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
            stop()
            return false
        }
    }

    @Synchronized
    override fun stop() {
        if (!isRecording && audioRecord == null) return
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord stop exception: ${e.message}")
        }

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord release exception: ${e.message}")
        }
        audioRecord = null
        Log.i(TAG, "PTT voice recording stopped and cleaned up")
    }
}
