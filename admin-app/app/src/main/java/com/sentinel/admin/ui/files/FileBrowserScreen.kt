package com.sentinel.admin.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sentinel.admin.domain.model.FileItem
import com.sentinel.admin.service.files.FileDownloadManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    deviceId: String,
    onBack: () -> Unit,
    viewModel: FileViewModel = hiltViewModel()
) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    LaunchedEffect(deviceId) {
        viewModel.loadDirectory(deviceId, "/sdcard")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("File Browser", style = MaterialTheme.typography.titleMedium)
                        Text(currentPath, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (currentPath == "/sdcard" || currentPath == "/") onBack()
                        else viewModel.navigateBack(deviceId)
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    items(files) { item ->
                        FileListItem(
                            item = item,
                            onClick = {
                                if (item.isDir) {
                                    val newPath = if (currentPath.endsWith("/")) "$currentPath${item.name}" else "$currentPath/${item.name}"
                                    viewModel.loadDirectory(deviceId, newPath)
                                } else {
                                    viewModel.downloadFile(deviceId, item)
                                }
                            }
                        )
                    }
                }
            }

            // Download Progress Overlay
            when (val state = downloadState) {
                is FileDownloadManager.DownloadState.Downloading -> {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Downloading...", style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                            Text(
                                "${formatSize(state.bytesReceived)} / ${formatSize(state.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is FileDownloadManager.DownloadState.Completed -> {
                    // Completed
                }
                is FileDownloadManager.DownloadState.Error -> {
                    // Error
                }
                else -> {}
            }
        }
    }
}

@Composable
fun FileListItem(item: FileItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = { 
            val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.lastModified))
            Text(if (item.isDir) date else "$date • ${formatSize(item.size)}")
        },
        leadingContent = {
            Icon(
                imageVector = if (item.isDir) Icons.Default.Folder else getFileIcon(item.name),
                contentDescription = null,
                tint = if (item.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun getFileIcon(name: String): ImageVector {
    val ext = name.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp" -> Icons.Default.Image
        "mp4", "mkv", "mov" -> Icons.Default.Movie
        "mp3", "wav", "ogg" -> Icons.Default.AudioFile
        "pdf" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}
