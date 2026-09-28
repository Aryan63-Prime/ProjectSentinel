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
    private val downloadDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
                activeJob = launch(ioDispatcher) {
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
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        val fileName = path.substringAfterLast("/")
        val localFile = File(downloadDir, fileName)
        val partFile = File(downloadDir, "$fileName.part")
        
        var sink: okio.BufferedSink? = null
        try {
            // If previous download finished completely and user initiated download again, start fresh
            if (localFile.exists() && !partFile.exists()) {
                localFile.delete()
            }

            var offset = if (partFile.exists()) partFile.length() else 0L
            var bytesReceived = offset
            var totalSize = -1L
            var expectedSha256: String? = null

            val sequence = System.currentTimeMillis()
            val req = messageSerializer.serializeFileDownloadReq(deviceId, path, offset, "", sequence)
            connectionRepository.sendText(req)

            val transferId = sequence.toInt()

            connectionRepository.events.collect { event ->
                when (event) {
                    is ConnectionEvent.FileDownloadReceived -> {
                        val incoming = messageSerializer.deserialize(event.rawJson)
                        if (incoming is com.sentinel.admin.data.remote.protocol.IncomingMessage.FileDownloadRes) {
                            if (incoming.success) {
                                totalSize = incoming.size
                                expectedSha256 = incoming.sha256

                                // If the remote file shrank or offset exceeds new size, reset
                                if (offset > totalSize && totalSize >= 0) {
                                    sink?.close()
                                    sink = null
                                    if (partFile.exists()) partFile.delete()
                                    offset = 0L
                                    bytesReceived = 0L
                                    val retryReq = messageSerializer.serializeFileDownloadReq(deviceId, path, 0L, "", sequence)
                                    connectionRepository.sendText(retryReq)
                                    return@collect
                                }

                                // If file is already fully downloaded in partFile
                                if (totalSize > 0 && offset >= totalSize) {
                                    if (expectedSha256 != null) {
                                        val actualSha256 = calculateSha256(partFile)
                                        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                                            partFile.delete()
                                            _state.value = DownloadState.Error("SHA-256 mismatch")
                                            throw CancellationException("Checksum failed")
                                        }
                                    }
                                    if (localFile.exists()) localFile.delete()
                                    partFile.renameTo(localFile)
                                    _state.value = DownloadState.Completed(localFile)
                                    throw CancellationException("Download complete")
                                }

                                if (totalSize > 0) {
                                    _state.value = DownloadState.Downloading(
                                        progress = bytesReceived.toFloat() / totalSize,
                                        bytesReceived = bytesReceived,
                                        totalBytes = totalSize
                                    )
                                }
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
                            if (sink == null) {
                                sink = java.io.FileOutputStream(partFile, true).sink().buffer()
                            }

                            val chunkData = data.copyOfRange(9, data.size)
                            sink!!.write(chunkData)
                            bytesReceived += chunkData.size
                            
                            if (totalSize > 0) {
                                _state.value = DownloadState.Downloading(
                                    progress = (bytesReceived.toFloat() / totalSize).coerceIn(0f, 1f),
                                    bytesReceived = bytesReceived,
                                    totalBytes = totalSize
                                )
                            }
                            
                            if ((chunkSeq + 1) % 5 == 0) {
                                val ack = messageSerializer.serializeFileChunkAck(deviceId, path, chunkSeq.toLong(), System.currentTimeMillis())
                                connectionRepository.sendText(ack)
                            }
                            
                            if (totalSize > 0 && bytesReceived >= totalSize) {
                                sink!!.flush()
                                sink!!.close()
                                sink = null

                                // Verify SHA-256 integrity
                                if (expectedSha256 != null) {
                                    val actualSha256 = calculateSha256(partFile)
                                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                                        partFile.delete()
                                        _state.value = DownloadState.Error("Checksum verification failed (SHA-256 mismatch)")
                                        throw CancellationException("Checksum failed")
                                    }
                                    Log.i(TAG, "File SHA-256 checksum verified: $actualSha256")
                                }

                                if (localFile.exists()) localFile.delete()
                                val renamed = partFile.renameTo(localFile)
                                val finalFile = if (renamed) localFile else partFile
                                _state.value = DownloadState.Completed(finalFile)
                                throw CancellationException("Download complete")
                            }
                        }
                    }
                    else -> {}
                }
            }

        } catch (e: CancellationException) {
            try { sink?.close() } catch (_: Exception) {}
            if (e.message == "Checksum failed") {
                // State already set to Error
            } else if (e.message != "Download complete") {
                // Cancelled or paused: retain partFile so user can resume later
                _state.value = DownloadState.Idle
            }
        } catch (e: Exception) {
            try { sink?.close() } catch (_: Exception) {}
            Log.e(TAG, "Download failed: ${e.message}")
            _state.value = DownloadState.Error(e.message ?: "Unknown error")
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
