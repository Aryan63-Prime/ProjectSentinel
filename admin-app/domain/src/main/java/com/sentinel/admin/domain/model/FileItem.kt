package com.sentinel.admin.domain.model

data class FileItem(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long
)
