package com.sentinel.host.domain.model

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long
)
