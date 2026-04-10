package com.koma.client.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.koma.client.domain.model.Download
import com.koma.client.domain.model.DownloadState
import com.koma.client.domain.repo.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val downloads: StateFlow<List<Download>> = downloadRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(bookId: String) {
        viewModelScope.launch { downloadRepository.delete(bookId) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            downloads.value.forEach { downloadRepository.delete(it.bookId) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(
    modifier: Modifier = Modifier,
    onOpenReader: ((bookId: String, mediaType: String) -> Unit)? = null,
    viewModel: OfflineViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val active = downloads.filter { it.state == DownloadState.DOWNLOADING }
    val queued = downloads.filter { it.state == DownloadState.QUEUED }
    val complete = downloads.filter { it.state == DownloadState.COMPLETE }
    val failed = downloads.filter { it.state == DownloadState.FAILED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    if (downloads.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete All") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Delete, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteAllDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "No downloads yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Download books for offline reading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (active.isNotEmpty()) {
                    item { SectionHeader("Downloading") }
                    items(active, key = { it.bookId }) { download ->
                        DownloadItem(
                            download = download,
                            onDelete = { viewModel.delete(download.bookId) },
                            onOpen = onOpenReader,
                        )
                    }
                }

                if (queued.isNotEmpty()) {
                    item { SectionHeader("Queued") }
                    items(queued, key = { it.bookId }) { download ->
                        DownloadItem(
                            download = download,
                            onDelete = { viewModel.delete(download.bookId) },
                            onOpen = onOpenReader,
                        )
                    }
                }

                if (complete.isNotEmpty()) {
                    item { SectionHeader("Completed") }
                    items(complete, key = { it.bookId }) { download ->
                        DownloadItem(
                            download = download,
                            onDelete = { viewModel.delete(download.bookId) },
                            onOpen = onOpenReader,
                        )
                    }
                }

                if (failed.isNotEmpty()) {
                    item { SectionHeader("Failed") }
                    items(failed, key = { it.bookId }) { download ->
                        DownloadItem(
                            download = download,
                            onDelete = { viewModel.delete(download.bookId) },
                            onOpen = onOpenReader,
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Downloads") },
            text = { Text("This will remove all downloaded files. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showDeleteAllDialog = false
                }) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DownloadItem(
    download: Download,
    onDelete: () -> Unit,
    onOpen: ((bookId: String, mediaType: String) -> Unit)?,
) {
    ListItem(
        headlineContent = {
            Text(download.bookTitle, maxLines = 1)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (download.state) {
                    DownloadState.DOWNLOADING -> {
                        val progress = if (download.totalBytes > 0) {
                            download.bytesDownloaded.toFloat() / download.totalBytes.toFloat()
                        } else null
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        val sizeText = if (download.totalBytes > 0) {
                            "${formatBytes(download.bytesDownloaded)} / ${formatBytes(download.totalBytes)}"
                        } else {
                            formatBytes(download.bytesDownloaded)
                        }
                        Text(sizeText, style = MaterialTheme.typography.bodySmall)
                    }
                    DownloadState.COMPLETE -> {
                        if (download.totalBytes > 0) {
                            Text(
                                formatBytes(download.totalBytes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    DownloadState.FAILED -> {
                        Text(
                            download.error ?: "Download failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    DownloadState.QUEUED -> {
                        Text(
                            "Waiting to download…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateBadge(download.state)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun StateBadge(state: DownloadState) {
    when (state) {
        DownloadState.DOWNLOADING -> {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        DownloadState.COMPLETE -> Badge(containerColor = MaterialTheme.colorScheme.primary) {
            Text("Done", style = MaterialTheme.typography.labelSmall)
        }
        DownloadState.FAILED -> Badge(containerColor = MaterialTheme.colorScheme.error) {
            Text("Error", style = MaterialTheme.typography.labelSmall)
        }
        DownloadState.QUEUED -> Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Text("Queued", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
