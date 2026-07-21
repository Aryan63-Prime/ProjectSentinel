package com.sentinel.host.data.repository

import com.sentinel.host.domain.model.FileItem
import com.sentinel.host.domain.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile

class FileRepositoryImpl : FileRepository {

    override fun listFiles(path: String): List<FileItem> {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) return emptyList()

        return root.listFiles()?.map { file ->
            FileItem(
                name = file.name,
                path = file.absolutePath,
                isDir = file.isDirectory,
                size = if (file.isDirectory) 0 else file.length(),
                lastModified = file.lastModified()
            )
        } ?: emptyList()
    }

    override fun getFileMetadata(path: String): FileItem? {
        val file = File(path)
        if (!file.exists()) return null
        return FileItem(
            name = file.name,
            path = file.absolutePath,
            isDir = file.isDirectory,
            size = if (file.isDirectory) 0 else file.length(),
            lastModified = file.lastModified()
        )
    }

    override fun openFile(path: String, offset: Long): Flow<ByteArray> = flow {
        val file = File(path)
        if (!file.exists() || file.isDirectory) return@flow

        val raf = RandomAccessFile(file, "r")
        try {
            raf.seek(offset)
            val buffer = ByteArray(64 * 1024) // 64KB chunks
            var bytesRead = raf.read(buffer)
            while (bytesRead != -1) {
                if (bytesRead < buffer.size) {
                    emit(buffer.copyOf(bytesRead))
                } else {
                    emit(buffer)
                }
                bytesRead = raf.read(buffer)
            }
        } finally {
            raf.close()
        }
    }.flowOn(Dispatchers.IO)
}
