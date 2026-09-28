# Feature Specification & Code: Bidirectional Voice Intercom (Push-To-Talk / PTT)

## 1. Overview & Problem Statement
Currently, Project Sentinel's audio subsystem is strictly unidirectional: the host streams microphone audio to the admin for monitoring. In field operations, emergency response, and fleet dispatch, administrators need the ability to **speak back to the host device** in real time (Walkie-Talkie / Push-To-Talk mode).

### Acoustic Echo Challenge:
When the host's loudspeaker plays the admin's incoming voice, the host microphone will pick up that sound and retransmit it back to the admin, causing a deafening **acoustic feedback loop**.
This feature implements:
1. **Hold-to-Talk Admin UI** with immediate Opus stream transmission.
2. **Host-Side `AudioTrack` Playback Engine**.
3. **Hardware `AcousticEchoCanceler` (AEC)** and `NoiseSuppressor` integration on the host to cancel out loudspeaker echo before re-encoding microphone audio.

---

## 2. Protocol Specification (`shared/`)

Add PTT frame types in [`shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/MessageType.kt):

```kotlin
object MessageType {
    // Existing types...
    const val PTT_START = "PTT_START"
    const val PTT_STOP = "PTT_STOP"
    const val PTT_AUDIO = "PTT_AUDIO" // Binary or base64 frame from Admin -> Host
}
```

---

## 3. Host-App Implementation

### File: `host-app/data/src/main/java/com/sentinel/host/data/audio/PttAudioPlayer.kt`

```kotlin
package com.sentinel.host.data.audio

import android.content.Context
import android.media.*
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
```

---

## 4. Admin-App Implementation

### File: `admin-app/app/src/main/java/com/sentinel/admin/ui/detail/PttButton.kt`

```kotlin
package com.sentinel.admin.ui.detail

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PttButton(
    isOnline: Boolean,
    onPressStart: () -> Unit,
    onPressRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(if (isPressed) 1.15f else 1.0f)
                .background(
                    if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    CircleShape
                )
                .pointerInteropFilter { motionEvent ->
                    if (!isOnline) return@pointerInteropFilter false
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isPressed = true
                            onPressStart()
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isPressed = false
                            onPressRelease()
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Push to Talk",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isPressed) "Transmitting Voice..." else "Hold to Speak (PTT)",
            style = MaterialTheme.typography.labelMedium,
            color = if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```
