package com.koma.client.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.res.painterResource
import com.koma.client.R
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.MediaType
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.model.Series
import com.koma.client.domain.repo.DownloadRepository
import com.koma.client.domain.repo.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailUiState(
    val loading: Boolean = true,
    val series: Series? = null,
    val books: List<Book> = emptyList(),
    val error: String? = null,
    val sortAscending: Boolean = true,
    val isSelectMode: Boolean = false,
    val selectedBookIds: Set<String> = emptySet(),
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val seriesId: String = savedStateHandle["seriesId"] ?: ""
    private val _state = MutableStateFlow(SeriesDetailUiState())
    val state: StateFlow<SeriesDetailUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            try {
                val server = serverRepo.getActive() ?: return@launch
                val mediaServer = registry.get(server)
                val series = mediaServer.seriesDetail(seriesId)
                _state.update { it.copy(series = series) }

                mediaServer.books(seriesId).collect { books ->
                    _state.update { it.copy(loading = false, books = books) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun toggleSort() {
        _state.update { it.copy(sortAscending = !it.sortAscending) }
    }

    fun toggleSelectMode() {
        _state.update { it.copy(isSelectMode = !it.isSelectMode, selectedBookIds = emptySet()) }
    }

    fun toggleBookSelection(bookId: String) {
        _state.update { s ->
            val newSet = if (bookId in s.selectedBookIds) {
                s.selectedBookIds - bookId
            } else {
                s.selectedBookIds + bookId
            }
            s.copy(selectedBookIds = newSet)
        }
    }

    fun markSelectedRead() {
        viewModelScope.launch {
            try {
                val server = serverRepo.getActive() ?: return@launch
                val mediaServer = registry.get(server)
                val selectedIds = _state.value.selectedBookIds
                val books = _state.value.books.filter { it.id in selectedIds }
                books.forEach { book ->
                    mediaServer.updateProgress(
                        bookId = book.id,
                        progress = ReadProgress(
                            bookId = book.id,
                            page = book.pageCount,
                            completed = true,
                        ),
                    )
                }
                // Optimistically update local state
                _state.update { s ->
                    val updatedBooks = s.books.map { book ->
                        if (book.id in selectedIds) {
                            book.copy(
                                readProgress = ReadProgress(
                                    bookId = book.id,
                                    page = book.pageCount,
                                    completed = true,
                                ),
                            )
                        } else book
                    }
                    s.copy(books = updatedBooks, isSelectMode = false, selectedBookIds = emptySet())
                }
            } catch (_: Exception) {
                // Ignore errors silently for now
            }
        }
    }

    fun downloadSelected() {
        viewModelScope.launch {
            try {
                val server = serverRepo.getActive() ?: return@launch
                val mediaServer = registry.get(server)
                val selectedIds = _state.value.selectedBookIds
                val books = _state.value.books.filter { it.id in selectedIds }
                val currentSeries = _state.value.series
                books.forEach { book ->
                    val url = mediaServer.fileUrl(book.id)
                    downloadRepository.enqueue(
                        bookId = book.id,
                        serverId = server.id,
                        title = book.title,
                        fileUrl = url,
                        mediaType = book.mediaType.name,
                        seriesId = book.seriesId,
                        seriesTitle = currentSeries?.title ?: book.seriesTitle,
                        thumbUrl = book.thumbUrl ?: currentSeries?.thumbUrl ?: "",
                    )
                }
                _state.update { it.copy(isSelectMode = false, selectedBookIds = emptySet()) }
            } catch (_: Exception) {
                // Ignore errors silently for now
            }
        }
    }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val mb = bytes / 1_048_576.0
    return if (mb >= 1) "%.1f MB".format(mb) else "${bytes / 1024} KB"
}

private fun formatAuthors(authors: List<Pair<String, String>>): String {
    if (authors.isEmpty()) return ""
    // Group by role
    val byRole = authors.groupBy { it.second.lowercase() }
    val writers = byRole["writer"] ?: byRole["story"] ?: emptyList()
    val artists = byRole["penciller"] ?: byRole["artist"] ?: byRole["art"] ?: emptyList()
    return when {
        writers.isNotEmpty() && artists.isNotEmpty() && writers.map { it.first } != artists.map { it.first } ->
            "Story: ${writers.first().first} • Art: ${artists.first().first}"
        authors.size == 1 -> "By: ${authors.first().first}"
        else -> "By: ${authors.distinctBy { it.first }.take(2).joinToString(", ") { it.first }}"
    }
}

private fun formatStatus(status: String?): String = when (status?.uppercase()) {
    "ONGOING" -> "Ongoing"
    "ENDED" -> "Completed"
    "HIATUS" -> "Hiatus"
    "ABANDONED" -> "Abandoned"
    else -> status ?: ""
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeriesDetailScreen(
    onBookClick: (bookId: String, mediaType: MediaType) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val sortedBooks = if (state.sortAscending) {
        state.books.sortedBy { it.number ?: Int.MAX_VALUE }
    } else {
        state.books.sortedByDescending { it.number ?: Int.MIN_VALUE }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.series?.title ?: "Series",
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
        bottomBar = {
            if (state.isSelectMode && state.selectedBookIds.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { viewModel.markSelectedRead() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mark Read")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.downloadSelected() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download")
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading && state.series == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.series == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val series = state.series ?: return@Scaffold
                var summaryExpanded by remember { mutableStateOf(false) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // --- Header: Cover + Metadata ---
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AsyncImage(
                                model = series.thumbUrl,
                                contentDescription = series.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(2f / 3f),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    series.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )

                                val authorLine = formatAuthors(series.authors)
                                if (authorLine.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        authorLine,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                val statusLabel = formatStatus(series.status)
                                if (statusLabel.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(statusLabel, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(24.dp),
                                    )
                                }

                                if (!series.releaseDate.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        series.releaseDate,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                val chaptersText = if (series.readBookCount > 0 || series.bookCount > 0) {
                                    "${series.readBookCount} / ${series.bookCount} chapters"
                                } else {
                                    "${series.bookCount} chapters"
                                }
                                Text(
                                    chaptersText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                if (!series.publisher.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        series.publisher,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // --- Start / Continue Reading button ---
                    item {
                        val firstUnread = sortedBooks.firstOrNull { it.readProgress?.completed != true }
                            ?: sortedBooks.firstOrNull()
                        if (firstUnread != null) {
                            val buttonLabel = if (series.readBookCount == 0) {
                                "Start Reading"
                            } else {
                                "Continue Reading"
                            }
                            Button(
                                onClick = { onBookClick(firstUnread.id, firstUnread.mediaType) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(buttonLabel)
                            }
                        }
                    }

                    // --- Summary (expandable) ---
                    series.summary?.let { summary ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (summary.length > 150) {
                                    TextButton(onClick = { summaryExpanded = !summaryExpanded }) {
                                        Text(if (summaryExpanded) "Show less" else "Show more")
                                    }
                                }
                            }
                        }
                    }

                    // --- Genres ---
                    if (series.genres.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text(
                                    "Genres",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    series.genres.forEach { genre ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(genre) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- Tags ---
                    if (series.tags.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text(
                                    "Tags",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    series.tags.forEach { tag ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(tag) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- Chapters header with sort + select ---
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Chapters",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row {
                                IconButton(onClick = { viewModel.toggleSort() }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_sort_order),
                                        contentDescription = "Toggle sort order",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                TextButton(onClick = { viewModel.toggleSelectMode() }) {
                                    if (state.isSelectMode) {
                                        Icon(Icons.Filled.Close, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Cancel")
                                    } else {
                                        Text("Select")
                                    }
                                }
                            }
                        }
                    }

                    // --- Book list ---
                    items(sortedBooks, key = { it.id }) { book ->
                        val isSelected = book.id in state.selectedBookIds
                        val isRead = book.readProgress?.completed == true

                        ListItem(
                            headlineContent = {
                                Text(
                                    book.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                val sizePart = formatFileSize(book.sizeBytes)
                                val pagesPart = "${book.pageCount} pages"
                                val detail = if (sizePart.isNotBlank()) "$pagesPart • $sizePart" else pagesPart
                                Text(detail, style = MaterialTheme.typography.bodySmall)
                            },
                            leadingContent = {
                                if (state.isSelectMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleBookSelection(book.id) },
                                    )
                                } else {
                                    AsyncImage(
                                        model = book.thumbUrl,
                                        contentDescription = book.title,
                                        modifier = Modifier.size(48.dp, 64.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            },
                            trailingContent = {
                                if (isRead) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Read",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                if (state.isSelectMode) {
                                    viewModel.toggleBookSelection(book.id)
                                } else {
                                    onBookClick(book.id, book.mediaType)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
