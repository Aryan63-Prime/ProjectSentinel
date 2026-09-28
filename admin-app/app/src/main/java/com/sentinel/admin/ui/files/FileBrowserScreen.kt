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
import com.sentinel.admin.ui.detail.FilePreviewDialog
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
    val previewPayload by viewModel.previewPayload.collectAsState()
    val isPreviewLoading by viewModel.isPreviewLoading.collectAsState()

    LaunchedEffect(deviceId) {
        viewModel.loadDirectory(deviceId, "/storage/emulated/0")
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
                        if (currentPath == "/storage/emulated/0" || currentPath == "/") onBack()
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
                            },
                            onPreview = {
                                viewModel.requestPreview(deviceId, item)
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
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(onClick = { viewModel.cancelDownload(deviceId) }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Download")
                            }
                        }
                    }
                }
                is FileDownloadManager.DownloadState.Completed -> {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Saved to Download/Sentinel/", style = MaterialTheme.typography.labelMedium)
                                Text(state.file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { viewModel.cancelDownload(deviceId) }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
                is FileDownloadManager.DownloadState.Error -> {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Download error: ${state.message}", style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { viewModel.cancelDownload(deviceId) }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
                else -> {}
            }

            // Remote File Preview Dialog
            previewPayload?.let { payload ->
                FilePreviewDialog(
                    payload = payload,
                    onDismiss = viewModel::dismissPreview,
                    onDownloadFullFile = { path ->
                        val fileName = path.substringAfterLast("/")
                        val item = files.firstOrNull { it.name == fileName }
                        if (item != null) {
                            viewModel.downloadFile(deviceId, item)
                        }
                        viewModel.dismissPreview()
                    }
                )
            }
        }
    }
}

@Composable
fun FileListItem(
    item: FileItem,
    onClick: () -> Unit,
    onPreview: () -> Unit = {}
) {
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
        trailingContent = {
            if (!item.isDir) {
                IconButton(onClick = onPreview) {
                    Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = MaterialTheme.colorScheme.primary)
                }
            }
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
