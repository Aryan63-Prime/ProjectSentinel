package com.sentinel.admin.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.admin.data.remote.protocol.MessageSerializer
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.FileItem
import com.sentinel.admin.domain.repository.ConnectionRepository
import com.sentinel.admin.service.files.FileDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val messageSerializer: MessageSerializer,
    private val downloadManager: FileDownloadManager
) : ViewModel() {

    private val _currentPath = MutableStateFlow("/sdcard")
    val currentPath = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    val downloadState = downloadManager.state

    init {
        connectionRepository.events
            .onEach { event ->
                if (event is ConnectionEvent.FilesListReceived) {
                    val incoming = messageSerializer.deserialize(event.rawJson)
                    if (incoming is com.sentinel.admin.data.remote.protocol.IncomingMessage.FilesListRes) {
                        _files.value = incoming.items.map { 
                            FileItem(it.name, it.is_dir, it.size, it.last_modified)
                        }.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
                        _isLoading.value = false
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadDirectory(deviceId: String, path: String) {
        _currentPath.value = path
        _isLoading.value = true
        val req = messageSerializer.serializeFilesListReq(deviceId, path, System.currentTimeMillis())
        connectionRepository.sendText(req)
    }

    fun navigateBack(deviceId: String) {
        val current = _currentPath.value
        if (current == "/" || current == "/sdcard") return
        
        val parent = current.substringBeforeLast("/", "/")
        loadDirectory(deviceId, if (parent.isEmpty()) "/" else parent)
    }

    fun downloadFile(deviceId: String, item: FileItem) {
        val path = if (_currentPath.value.endsWith("/")) "${_currentPath.value}${item.name}" else "${_currentPath.value}/${item.name}"
        downloadManager.startDownload(deviceId, path)
    }
}
