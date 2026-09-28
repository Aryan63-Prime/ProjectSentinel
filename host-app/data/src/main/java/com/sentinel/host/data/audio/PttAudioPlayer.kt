package com.sentinel.host.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PttAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:PttPlayer"
        private const val SAMPLE_RATE = 16000 // 16 kHz Wideband Voice
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var isPlaying = false

    @Synchronized
    fun startPttSession(micAudioRecordSessionId: Int? = null) {
        if (isPlaying) return

        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // Route through voice call pipeline for hardware DSP
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // Enable hardware Acoustic Echo Cancellation (AEC) if mic session ID is available
        if (micAudioRecordSessionId != null && AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(micAudioRecordSessionId)?.apply {
                    enabled = true
                }
                Log.i(TAG, "Hardware AcousticEchoCanceler enabled on session $micAudioRecordSessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize AcousticEchoCanceler: ${e.message}")
            }
        }

        if (micAudioRecordSessionId != null && NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(micAudioRecordSessionId)?.apply {
                    enabled = true
                }
                Log.i(TAG, "Hardware NoiseSuppressor enabled on session $micAudioRecordSessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize NoiseSuppressor: ${e.message}")
            }
        }

        audioTrack?.play()
        isPlaying = true
        Log.i(TAG, "PTT loudspeaker playback started")
    }

    suspend fun playPcmChunk(pcmData: ByteArray) = withContext(Dispatchers.IO) {
        if (!isPlaying || audioTrack == null) return@withContext
        try {
            audioTrack?.write(pcmData, 0, pcmData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing PCM to AudioTrack: ${e.message}")
        }
    }

    @Synchronized
    fun stopPttSession() {
        if (!isPlaying) return
        try {
            echoCanceler?.release()
            echoCanceler = null

            noiseSuppressor?.release()
            noiseSuppressor = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping PTT player: ${e.message}")
        } finally {
            isPlaying = false
            Log.i(TAG, "PTT loudspeaker playback stopped")
        }
    }
}
