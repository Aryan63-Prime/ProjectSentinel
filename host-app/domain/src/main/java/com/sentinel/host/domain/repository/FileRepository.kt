package com.sentinel.host.domain.repository

import com.sentinel.host.domain.model.FileItem
import kotlinx.coroutines.flow.Flow

interface FileRepository {
    fun listFiles(path: String): List<FileItem>
    fun getFileMetadata(path: String): FileItem?
    fun openFile(path: String, offset: Long): Flow<ByteArray>
}
