package com.sentinel.host.service

import android.util.Log
import com.sentinel.host.data.remote.protocol.MessageSerializer
import com.sentinel.host.domain.model.FileItem
import com.sentinel.host.domain.repository.ConnectionRepository
import com.sentinel.host.domain.repository.FileRepository
import com.sentinel.shared.protocol.MessageType
import com.sentinel.shared.protocol.PacketType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class FileStreamer(
    private val fileRepository: FileRepository,
    private val connectionRepository: ConnectionRepository,
    private val messageSerializer: MessageSerializer,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "Sentinel:FileStream"
        private const val CHUNK_WINDOW_SIZE = 5 // Send 5 chunks before needing an ACK
    }

    private val activeTransfers = mutableMapOf<String, Job>()
    private val transferMutex = Mutex()
    
    private val ackFlow = MutableSharedFlow<Long>(extraBufferCapacity = 64)

    fun handleFilesListReq(path: String, sequence: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val sanitizedPath = sanitizePath(path)
                val items = fileRepository.listFiles(sanitizedPath)
                val response = serializeListRes(sanitizedPath, items, sequence)
                connectionRepository.sendText(response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list files: ${e.message}")
                connectionRepository.sendText(messageSerializer.serializeError(sequence, 500, e.message ?: "Internal Error"))
            }
        }
    }

    fun handleFileDownloadReq(path: String, offset: Long, nonce: String, sequence: Long) {
        scope.launch {
            transferMutex.withLock {
                activeTransfers[path]?.cancel()
                val job = launch(Dispatchers.IO) {
                    runTransfer(path, offset, sequence)
                }
                activeTransfers[path] = job
            }
        }
    }

    fun handleFileStopReq(path: String) {
        scope.launch {
            transferMutex.withLock {
                activeTransfers[path]?.cancel()
                activeTransfers.remove(path)
                Log.i(TAG, "Transfer stopped for $path")
            }
        }
    }

    fun handleChunkAck(sequence: Long) {
        ackFlow.tryEmit(sequence)
    }

    private suspend fun runTransfer(path: String, offset: Long, sequence: Long) {
        try {
            val sanitizedPath = sanitizePath(path)
            val metadata = fileRepository.getFileMetadata(sanitizedPath)
                ?: throw Exception("File not found")

            // Send initial metadata response
            val res = serializeDownloadRes(sanitizedPath, true, metadata.size, sequence)
            connectionRepository.sendText(res)

            // Transfer ID is the sequence of the request
            val transferId = sequence.toInt()

            fileRepository.openFile(sanitizedPath, offset).collectIndexed { index, chunk ->
                // Flow control: wait for ACK every CHUNK_WINDOW_SIZE chunks
                if (index > 0 && index % CHUNK_WINDOW_SIZE == 0) {
                    withTimeout(10000) {
                        ackFlow.first { it >= index.toLong() }
                    }
                }

                sendBinaryChunk(transferId, index.toLong(), chunk)
            }
            
            Log.i(TAG, "Transfer complete for $sanitizedPath")
        } catch (e: Exception) {
            Log.e(TAG, "Transfer failed for $path: ${e.message}")
            if (e !is CancellationException) {
                val errRes = serializeDownloadRes(path, false, 0, sequence, e.message)
                connectionRepository.sendText(errRes)
            }
        } finally {
            transferMutex.withLock {
                activeTransfers.remove(path)
            }
        }
    }

    private fun sendBinaryChunk(transferId: Int, sequence: Long, data: ByteArray) {
        val buffer = Buffer()
        buffer.writeByte(PacketType.FILE_CHUNK.toInt())
        buffer.writeInt(transferId)
        buffer.writeInt(sequence.toInt())
        buffer.write(data)
        connectionRepository.sendBinary(buffer.readByteArray())
    }

    private fun sanitizePath(path: String): String {
        val file = File(path)
        val canonical = file.canonicalPath
        // Basic check to ensure it's not trying to escape a reasonable boundary
        // Even if we allow full access, canonicalization avoids ../ complexity
        return canonical
    }

    // Manual serialization until I add them to MessageSerializer
    private fun serializeListRes(path: String, items: List<FileItem>, sequence: Long): String {
        val buffer = Buffer()
        val writer = com.squareup.moshi.JsonWriter.of(buffer)
        writer.beginObject()
        writer.name("type").value(MessageType.FILES_LIST_RES)
        writer.name("version").value(1)
        writer.name("timestamp").value(System.currentTimeMillis())
        writer.name("sequence").value(sequence)
        writer.name("data").beginObject()
        writer.name("path").value(path)
        writer.name("items").beginArray()
        items.forEach { item ->
            writer.beginObject()
            writer.name("name").value(item.name)
            writer.name("is_dir").value(item.isDir)
            writer.name("size").value(item.size)
            writer.name("last_modified").value(item.lastModified)
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()
        writer.endObject()
        writer.close()
        return buffer.readUtf8()
    }

    private fun serializeDownloadRes(path: String, success: Boolean, size: Long, sequence: Long, error: String? = null): String {
        val buffer = Buffer()
        val writer = com.squareup.moshi.JsonWriter.of(buffer)
        writer.beginObject()
        writer.name("type").value(MessageType.FILE_DOWNLOAD_RES)
        writer.name("version").value(1)
        writer.name("timestamp").value(System.currentTimeMillis())
        writer.name("sequence").value(sequence)
        writer.name("data").beginObject()
        writer.name("path").value(path)
        writer.name("success").value(success)
        writer.name("size").value(size)
        if (error != null) writer.name("error").value(error)
        writer.endObject()
        writer.endObject()
        writer.close()
        return buffer.readUtf8()
    }
}
