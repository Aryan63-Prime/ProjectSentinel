package com.sentinel.admin.service.files

import android.util.Log
import com.sentinel.admin.data.remote.protocol.MessageSerializer
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.repository.ConnectionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.buffer
import okio.sink
import java.io.File
import java.nio.ByteBuffer

class FileDownloadManager(
    private val connectionRepository: ConnectionRepository,
    private val messageSerializer: MessageSerializer,
    private val scope: CoroutineScope,
    private val downloadDir: File
) {
    companion object {
        private const val TAG = "Sentinel:FileDown"
    }

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val progress: Float, val bytesReceived: Long, val totalBytes: Long) : DownloadState()
        data class Completed(val file: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state = _state.asStateFlow()

    private var activeJob: Job? = null
    private val mutex = Mutex()

    fun startDownload(deviceId: String, path: String) {
        scope.launch {
            mutex.withLock {
                activeJob?.cancel()
                activeJob = launch(Dispatchers.IO) {
                    runDownload(deviceId, path)
                }
            }
        }
    }

    fun cancelDownload() {
        scope.launch {
            mutex.withLock {
                activeJob?.cancel()
                activeJob = null
                _state.value = DownloadState.Idle
            }
        }
    }

    private suspend fun runDownload(deviceId: String, path: String) {
        val fileName = path.substringAfterLast("/")
        val localFile = File(downloadDir, fileName)
        
        try {
            val sequence = System.currentTimeMillis()
            val req = messageSerializer.serializeFileDownloadReq(deviceId, path, 0L, "", sequence)
            connectionRepository.sendText(req)

            val sink = localFile.sink().buffer()
            var bytesReceived = 0L
            var totalSize = -1L
            val transferId = sequence.toInt()

            connectionRepository.events.collect { event ->
                when (event) {
                    is ConnectionEvent.FileDownloadReceived -> {
                        val incoming = messageSerializer.deserialize(event.rawJson)
                        if (incoming is com.sentinel.admin.data.remote.protocol.IncomingMessage.FileDownloadRes) {
                            if (incoming.success) {
                                totalSize = incoming.size
                            } else {
                                throw Exception(incoming.error ?: "Unknown error")
                            }
                        }
                    }
                    is ConnectionEvent.FileChunkReceived -> {
                        val data = event.data
                        if (data.size < 9) return@collect
                        
                        val bb = ByteBuffer.wrap(data)
                        val type = bb.get()
                        val incomingId = bb.getInt()
                        val chunkSeq = bb.getInt()
                        
                        if (incomingId == transferId) {
                            val chunkData = data.copyOfRange(9, data.size)
                            sink.write(chunkData)
                            bytesReceived += chunkData.size
                            
                            if (totalSize > 0) {
                                _state.value = DownloadState.Downloading(
                                    progress = bytesReceived.toFloat() / totalSize,
                                    bytesReceived = bytesReceived,
                                    totalBytes = totalSize
                                )
                            }
                            
                            if (chunkSeq > 0 && chunkSeq % 5 == 0) {
                                val ack = messageSerializer.serializeFileChunkAck(deviceId, path, chunkSeq.toLong(), System.currentTimeMillis())
                                connectionRepository.sendText(ack)
                            }
                            
                            if (totalSize > 0 && bytesReceived >= totalSize) {
                                sink.flush()
                                sink.close()
                                _state.value = DownloadState.Completed(localFile)
                                throw CancellationException("Download complete")
                            }
                        }
                    }
                    else -> {}
                }
            }

        } catch (e: CancellationException) {
            if (e.message != "Download complete") {
                if (localFile.exists()) localFile.delete()
                _state.value = DownloadState.Error("Cancelled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            _state.value = DownloadState.Error(e.message ?: "Unknown error")
            if (localFile.exists()) localFile.delete()
        }
    }
}
