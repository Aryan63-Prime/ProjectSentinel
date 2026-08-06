package com.sentinel.admin.ui.recordings

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class RecordingItem(
    val file: File,
    val name: String,
    val formattedDate: String,
    val formattedSize: String,
    val durationText: String,
    val isPlaying: Boolean = false
)

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "Sentinel:RecordingsVM"
    }

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _currentlyPlayingPath = MutableStateFlow<String?>(null)
    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        viewModelScope.launch {
            try {
                val recordingsDir = File(context.getExternalFilesDir(null), "Recordings")
                if (!recordingsDir.exists()) {
                    recordingsDir.mkdirs()
                }

                val files = recordingsDir.listFiles { _, name -> name.endsWith(".wav", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()

                val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                val playingPath = _currentlyPlayingPath.value

                val items = files.map { file ->
                    val sizeKb = file.length() / 1024
                    val sizeText = if (sizeKb > 1024) String.format(Locale.US, "%.1f MB", sizeKb / 1024f) else "$sizeKb KB"
                    val dateText = dateFormat.format(Date(file.lastModified()))

                    RecordingItem(
                        file = file,
                        name = file.name,
                        formattedDate = dateText,
                        formattedSize = sizeText,
                        durationText = "WAV Audio",
                        isPlaying = file.absolutePath == playingPath
                    )
                }

                _recordings.value = items
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recordings: ${e.message}", e)
            }
        }
    }

    fun playRecording(recording: RecordingItem) {
        if (_currentlyPlayingPath.value == recording.file.absolutePath) {
            stopPlayback()
            return
        }

        stopPlayback()
        try {
            val player = MediaPlayer().apply {
                setDataSource(recording.file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            mediaPlayer = player
            _currentlyPlayingPath.value = recording.file.absolutePath
            loadRecordings()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio recording: ${e.message}", e)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaPlayer = null
            _currentlyPlayingPath.value = null
            loadRecordings()
        }
    }

    fun deleteRecording(recording: RecordingItem) {
        viewModelScope.launch {
            if (_currentlyPlayingPath.value == recording.file.absolutePath) {
                stopPlayback()
            }
            try {
                if (recording.file.exists()) {
                    recording.file.delete()
                    Log.i(TAG, "Deleted recording: ${recording.name}")
                }
                loadRecordings()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete recording: ${e.message}", e)
            }
        }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
