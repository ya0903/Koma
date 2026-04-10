package com.koma.client.ui.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavBackStackEntry
import coil3.compose.AsyncImage
import com.koma.client.domain.model.Download
import com.koma.client.domain.model.DownloadState
import com.koma.client.domain.repo.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Data model for grouped series ────────────────────────────────────────────

data class DownloadedSeries(
    val seriesId: String,
    val seriesTitle: String,
    val thumbUrl: String,
    val downloadCount: Int,
    val totalBytes: Long,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class OfflineViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val downloads: StateFlow<List<Download>> = downloadRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val seriesGroups: StateFlow<List<DownloadedSeries>> = downloadRepository
        .observeAll()
        .map { list ->
            list
                .filter { it.state == DownloadState.COMPLETE || it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }
                .groupBy { it.seriesId.ifBlank { it.bookId } }
                .map { (groupKey, books) ->
                    DownloadedSeries(
                        seriesId = groupKey,
                        seriesTitle = books.first().seriesTitle.ifBlank { books.first().bookTitle },
                        thumbUrl = books.first().thumbUrl,
                        downloadCount = books.size,
                        totalBytes = books.sumOf { it.totalBytes },
                    )
                }
                .sortedBy { it.seriesTitle }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun downloadsForSeries(seriesId: String): List<Download> {
        val all = downloads.value
        return all.filter { download ->
            val groupKey = download.seriesId.ifBlank { download.bookId }
            groupKey == seriesId
        }
    }

    fun delete(bookId: String) {
        viewModelScope.launch { downloadRepository.delete(bookId) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            downloads.value.forEach { downloadRepository.delete(it.bookId) }
        }
    }

    fun deleteSeriesDownloads(seriesId: String) {
        viewModelScope.launch {
            downloadsForSeries(seriesId).forEach { downloadRepository.delete(it.bookId) }
        }
    }
}

// ─── Top-level offline nav ─────────────────────────────────────────────────────

@Composable
fun OfflineScreen(
    modifier: Modifier = Modifier,
    onOpenReader: ((bookId: String, mediaType: String) -> Unit)? = null,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "offline_grid", modifier = modifier) {
        composable("offline_grid") {
            OfflineSeriesGrid(
                onSeriesClick = { seriesId -> navController.navigate("offline_chapters/$seriesId") },
            )
        }
        composable("offline_chapters/{seriesId}") { backStack ->
            val seriesId = backStack.arguments?.getString("seriesId") ?: return@composable
            OfflineChaptersScreen(
                seriesId = seriesId,
                onBookClick = { bookId, mediaType -> onOpenReader?.invoke(bookId, mediaType) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

// ─── Screen 1: Series grid ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSeriesGrid(
    onSeriesClick: (seriesId: String) -> Unit,
    viewModel: OfflineViewModel = hiltViewModel(),
) {
    val groups by viewModel.seriesGroups.collectAsStateWithLifecycle()
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    if (groups.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete all")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(groups, key = { it.seriesId }) { group ->
                    DownloadedSeriesCard(
                        series = group,
                        onClick = { onSeriesClick(group.seriesId) },
                    )
                }
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
private fun DownloadedSeriesCard(
    series: DownloadedSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column {
            AsyncImage(
                model = series.thumbUrl.ifBlank { null },
                contentDescription = series.seriesTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = series.seriesTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val countLabel = if (series.downloadCount == 1) {
                    "1 chapter"
                } else {
                    "${series.downloadCount} chapters"
                }
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Screen 2: Chapters within a series ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineChaptersScreen(
    seriesId: String,
    onBookClick: (bookId: String, mediaType: String) -> Unit,
    onBack: () -> Unit,
    viewModel: OfflineViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val seriesDownloads = downloads.filter { download ->
        val groupKey = download.seriesId.ifBlank { download.bookId }
        groupKey == seriesId
    }
    val seriesTitle = seriesDownloads.firstOrNull()?.seriesTitle?.ifBlank { null }
        ?: seriesDownloads.firstOrNull()?.bookTitle
        ?: "Downloads"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        seriesTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (seriesDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No chapters found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(seriesDownloads, key = { it.bookId }) { download ->
                    ChapterDownloadItem(
                        download = download,
                        onDelete = { viewModel.delete(download.bookId) },
                        onOpen = { onBookClick(download.bookId, download.mediaType) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterDownloadItem(
    download: Download,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = download.state == DownloadState.COMPLETE) {
            onOpen()
        },
        headlineContent = {
            Text(download.bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            AsyncImage(
                model = download.thumbUrl.ifBlank { null },
                contentDescription = download.bookTitle,
                modifier = Modifier.size(48.dp, 64.dp),
                contentScale = ContentScale.Crop,
            )
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
                            "Waiting to download...",
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
