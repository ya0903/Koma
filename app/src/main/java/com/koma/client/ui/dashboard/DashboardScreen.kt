package com.koma.client.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.koma.client.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.LibraryStats
import com.koma.client.domain.model.MediaType
import com.koma.client.domain.model.Series
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.MediaServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val libraries: List<Library> = emptyList(),
    val selectedLibraryId: String? = null,
    val onDeckBooks: List<Book> = emptyList(),
    val recentlyReleasedBooks: List<Book> = emptyList(),
    val recentlyAddedBooks: List<Book> = emptyList(),
    val recentlyAddedSeries: List<Series> = emptyList(),
    val recentlyUpdatedSeries: List<Series> = emptyList(),
    val recentlyReadBooks: List<Book> = emptyList(),
    val error: String? = null,
    /** Stats keyed by library ID; null key = "All Libraries" aggregate */
    val libraryStats: Map<String?, LibraryStats> = emptyMap(),
    val availableGenres: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val availablePublishers: List<String> = emptyList(),
    val searchFilters: SearchFilters = SearchFilters(),
    val searchResultSeries: List<Series> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        loadDashboard()
    }

    fun selectLibrary(libraryId: String?) {
        _state.value = _state.value.copy(selectedLibraryId = libraryId)
        loadDashboard(libraryId)
    }

    fun updateFilters(filters: SearchFilters) {
        _state.value = _state.value.copy(searchFilters = filters)
        if (filters.query.isNotBlank() || filters.activeFilterCount > 0) {
            serverSearch(filters)
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun serverSearch(filters: SearchFilters) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Debounce 300ms so we don't fire on every keystroke
            kotlinx.coroutines.delay(300)
            try {
                val server = serverRepo.getActive() ?: return@launch
                val mediaServer = registry.get(server)
                val results = if (filters.query.isNotBlank()) {
                    val searchResults = mediaServer.search(filters.query, com.koma.client.domain.server.SearchFilter())
                    // Combine series from search + filter by status/genre if set
                    var series = searchResults.series
                    if (filters.status != null) {
                        series = series.filter { it.status == filters.status }
                    }
                    if (filters.genres.isNotEmpty()) {
                        series = series.filter { s -> s.genres.any { it in filters.genres } }
                    }
                    if (filters.tags.isNotEmpty()) {
                        series = series.filter { s -> s.tags.any { it in filters.tags } }
                    }
                    series
                } else {
                    // No query but has filters — filter the loaded dashboard series
                    val allSeries = _state.value.recentlyAddedSeries + _state.value.recentlyUpdatedSeries
                    val unique = allSeries.distinctBy { it.id }
                    var filtered = unique
                    if (filters.status != null) {
                        filtered = filtered.filter { it.status == filters.status }
                    }
                    if (filters.genres.isNotEmpty()) {
                        filtered = filtered.filter { s -> s.genres.any { it in filters.genres } }
                    }
                    filtered
                }
                _state.value = _state.value.copy(searchResultSeries = results)
            } catch (_: Exception) {
                // Search failure is non-fatal — keep existing results
            }
        }
    }

    fun loadDashboard(libraryId: String? = _state.value.selectedLibraryId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val server = serverRepo.getActive()
                if (server == null) {
                    _state.value = _state.value.copy(loading = false, error = "No active server")
                    return@launch
                }
                val mediaServer = registry.get(server)

                val libraries = mediaServer.libraries().first()
                val onDeck = mediaServer.onDeckBooks(libraryId)
                val recentlyReleasedBooks = mediaServer.recentlyReleasedBooks(libraryId)
                val recentlyAddedBooks = mediaServer.recentlyAddedBooks(libraryId)
                val recentlyAddedSeries = mediaServer.recentlyAddedSeries(libraryId)
                val recentlyUpdatedSeries = mediaServer.recentlyUpdatedSeries(libraryId)
                val recentlyReadBooks = mediaServer.recentlyReadBooks(libraryId)

                // Server-side filtering is used — no client-side filter needed
                _state.value = _state.value.copy(
                    loading = false,
                    libraries = libraries,
                    onDeckBooks = onDeck,
                    recentlyReleasedBooks = recentlyReleasedBooks,
                    recentlyAddedBooks = recentlyAddedBooks,
                    recentlyAddedSeries = recentlyAddedSeries,
                    recentlyUpdatedSeries = recentlyUpdatedSeries,
                    recentlyReadBooks = recentlyReadBooks,
                )

                // Load library stats and filter metadata in background
                loadLibraryStatsAndMeta(mediaServer, libraries)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Unknown error",
                )
            }
        }
    }

    private fun loadLibraryStatsAndMeta(mediaServer: MediaServer, libraries: List<Library>) {
        viewModelScope.launch {
            try {
                val statsMap = mutableMapOf<String?, LibraryStats>()

                // All-libraries aggregate
                val allStatsDeferred = async { runCatching { mediaServer.libraryStats(null) }.getOrNull() }
                // Per-library stats
                val perLibDeferred = libraries.map { lib ->
                    lib.id to async { runCatching { mediaServer.libraryStats(lib.id) }.getOrNull() }
                }

                allStatsDeferred.await()?.let { statsMap[null] = it }
                perLibDeferred.forEach { (id, deferred) ->
                    deferred.await()?.let { statsMap[id] = it }
                }

                _state.value = _state.value.copy(libraryStats = statsMap)
            } catch (_: Exception) { /* stats are best-effort */ }
        }
        viewModelScope.launch {
            try {
                val genres = async { runCatching { mediaServer.availableGenres() }.getOrDefault(emptyList()) }
                val tags = async { runCatching { mediaServer.availableTags() }.getOrDefault(emptyList()) }
                val publishers = async { runCatching { mediaServer.availablePublishers() }.getOrDefault(emptyList()) }
                _state.value = _state.value.copy(
                    availableGenres = genres.await(),
                    availableTags = tags.await(),
                    availablePublishers = publishers.await(),
                )
            } catch (_: Exception) { /* filter meta is best-effort */ }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBookClick: (bookId: String, mediaType: MediaType) -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onSeeAllLibraries: () -> Unit,
    onSeeAll: (libraryId: String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var libraryMenuExpanded by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var filterSheetVisible by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val filters = state.searchFilters
    val searchActive = searchVisible && (filters.query.isNotBlank() || filters.activeFilterCount > 0)

    val selectedLibraryName = when (val id = state.selectedLibraryId) {
        null -> "All Libraries"
        else -> state.libraries.find { it.id == id }?.name ?: "All Libraries"
    }

    val effectiveLibraryId = state.selectedLibraryId ?: "all"

    if (filterSheetVisible) {
        SearchFilterSheet(
            filters = filters,
            availableGenres = state.availableGenres,
            availableTags = state.availableTags,
            availablePublishers = state.availablePublishers,
            sheetState = filterSheetState,
            onFiltersChanged = { viewModel.updateFilters(it) },
            onDismiss = { filterSheetVisible = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
                title = {
                    Text(
                        "Koma",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    // Search toggle
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) {
                            // Clear query but keep sort/filter preferences
                            viewModel.updateFilters(filters.copy(query = ""))
                        }
                    }) {
                        Icon(
                            if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchVisible) "Close search" else "Search",
                        )
                    }
                    // Filter button — shown only when search is visible
                    if (searchVisible) {
                        BadgedBox(
                            badge = {
                                if (filters.activeFilterCount > 0) {
                                    Badge { Text(filters.activeFilterCount.toString()) }
                                }
                            },
                        ) {
                            IconButton(onClick = { filterSheetVisible = true }) {
                                Icon(
                                    ImageVector.vectorResource(id = R.drawable.ic_filter),
                                    contentDescription = "Filters",
                                )
                            }
                        }
                    }
                    // Library filter dropdown
                    Box {
                        IconButton(onClick = { libraryMenuExpanded = true }) {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_library),
                                contentDescription = "Libraries: $selectedLibraryName",
                            )
                        }
                        DropdownMenu(
                            expanded = libraryMenuExpanded,
                            onDismissRequest = { libraryMenuExpanded = false },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            // Header
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Libraries",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        ),
                                    )
                                },
                                onClick = {},
                                enabled = false,
                            )
                            HorizontalDivider()
                            // All Libraries with counts
                            val allStats = state.libraryStats[null]
                            DropdownMenuItem(
                                text = {
                                    LibraryMenuItemContent(
                                        name = "All Libraries",
                                        stats = allStats,
                                        isSelected = state.selectedLibraryId == null,
                                    )
                                },
                                trailingIcon = if (state.selectedLibraryId == null) {
                                    {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else null,
                                onClick = {
                                    libraryMenuExpanded = false
                                    viewModel.selectLibrary(null)
                                },
                            )
                            HorizontalDivider()
                            // Individual libraries with counts
                            state.libraries.forEach { library ->
                                val isSelected = state.selectedLibraryId == library.id
                                val libStats = state.libraryStats[library.id]
                                DropdownMenuItem(
                                    text = {
                                        LibraryMenuItemContent(
                                            name = library.name,
                                            stats = libStats,
                                            isSelected = isSelected,
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        libraryMenuExpanded = false
                                        viewModel.selectLibrary(library.id)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.error != null && !state.loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { viewModel.loadDashboard() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                val searchQuery = filters.query

                val filteredAddedSeries = if (searchQuery.isBlank()) state.recentlyAddedSeries
                    else state.recentlyAddedSeries.filter { it.title.contains(searchQuery, ignoreCase = true) }
                val filteredUpdatedSeries = if (searchQuery.isBlank()) state.recentlyUpdatedSeries
                    else state.recentlyUpdatedSeries.filter { it.title.contains(searchQuery, ignoreCase = true) }
                val filteredOnDeck = if (searchQuery.isBlank()) state.onDeckBooks
                    else state.onDeckBooks.filter { it.title.contains(searchQuery, ignoreCase = true) }
                val filteredReleasedBooks = if (searchQuery.isBlank()) state.recentlyReleasedBooks
                    else state.recentlyReleasedBooks.filter { it.title.contains(searchQuery, ignoreCase = true) }
                val filteredAddedBooks = if (searchQuery.isBlank()) state.recentlyAddedBooks
                    else state.recentlyAddedBooks.filter { it.title.contains(searchQuery, ignoreCase = true) }
                val filteredReadBooks = if (searchQuery.isBlank()) state.recentlyReadBooks
                    else state.recentlyReadBooks.filter { it.title.contains(searchQuery, ignoreCase = true) }

                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { viewModel.loadDashboard() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search bar pinned above scroll
                        if (searchVisible) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateFilters(filters.copy(query = it)) },
                                    placeholder = { Text("Search...", style = MaterialTheme.typography.bodyMedium) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    trailingIcon = if (searchQuery.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { viewModel.updateFilters(filters.copy(query = "")) }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    } else null,
                                )
                            }
                        }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {

                        if (searchActive) {
                            // Show search/filter results grid
                            val results = state.searchResultSeries
                            if (results.isEmpty() && !state.loading) {
                                item {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "No results for \"$searchQuery\"",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                items(results.chunked(3)) { chunk ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        chunk.forEach { series ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                SeriesCard(
                                                    series = series,
                                                    onClick = { onSeriesClick(series.id) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                        // Fill remaining slots if chunk < 3
                                        repeat(3 - chunk.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            // Normal carousel view

                            // 1. On Deck
                            if (filteredOnDeck.isNotEmpty()) {
                                item {
                                    DashboardSection(
                                        title = "On Deck",
                                        onSeeAll = { onSeeAll(effectiveLibraryId) },
                                    ) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(filteredOnDeck, key = { it.id }) { book ->
                                                BookCard(
                                                    book = book,
                                                    onClick = { onBookClick(book.id, book.mediaType) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Recently Released Books
                            if (filteredReleasedBooks.isNotEmpty()) {
                                item {
                                    DashboardSection(
                                        title = "Recently Released Books",
                                        onSeeAll = { onSeeAll(effectiveLibraryId) },
                                    ) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(filteredReleasedBooks, key = { it.id }) { book ->
                                                BookCard(
                                                    book = book,
                                                    onClick = { onBookClick(book.id, book.mediaType) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Recently Added Books
                            if (filteredAddedBooks.isNotEmpty()) {
                                item {
                                    DashboardSection(
                                        title = "Recently Added Books",
                                        onSeeAll = { onSeeAll(effectiveLibraryId) },
                                    ) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(filteredAddedBooks, key = { it.id }) { book ->
                                                BookCard(
                                                    book = book,
                                                    onClick = { onBookClick(book.id, book.mediaType) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Recently Added Series
                            if (filteredAddedSeries.isNotEmpty()) {
                                item {
                                    DashboardSection(
                                        title = "Recently Added Series",
                                        onSeeAll = { onSeeAll(effectiveLibraryId) },
                                    ) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(filteredAddedSeries, key = { it.id }) { series ->
                                                SeriesCard(
                                                    series = series,
                                                    onClick = { onSeriesClick(series.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Recently Updated Series
                            if (filteredUpdatedSeries.isNotEmpty()) {
                                item {
                                    DashboardSection(
                                        title = "Recently Updated Series",
                                        onSeeAll = { onSeeAll(effectiveLibraryId) },
                                    ) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(filteredUpdatedSeries, key = { it.id }) { series ->
                                                SeriesCard(
                                                    series = series,
                                                    onClick = { onSeriesClick(series.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 6. Recently Read Books
                            if (filteredReadBooks.isNotEmpty()) {
                                item {
                                    DashboardSection(
                                        title = "Recently Read Books",
                                        onSeeAll = { onSeeAll(effectiveLibraryId) },
                                    ) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(filteredReadBooks, key = { it.id }) { book ->
                                                BookCard(
                                                    book = book,
                                                    onClick = { onBookClick(book.id, book.mediaType) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Empty state
                            if (!state.loading &&
                                filteredOnDeck.isEmpty() &&
                                filteredReleasedBooks.isEmpty() &&
                                filteredAddedBooks.isEmpty() &&
                                filteredAddedSeries.isEmpty() &&
                                filteredUpdatedSeries.isEmpty() &&
                                filteredReadBooks.isEmpty()
                            ) {
                                item {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            if (searchQuery.isNotBlank()) "No results for \"$searchQuery\""
                                            else "No content found",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    } // Column
                }
            }
        }
    }
}

@Composable
private fun LibraryMenuItemContent(
    name: String,
    stats: LibraryStats?,
    isSelected: Boolean,
) {
    Column {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
        if (stats != null) {
            Text(
                "%,d series \u2022 %,d books".format(stats.seriesCount, stats.bookCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardSection(
    title: String,
    onSeeAll: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSeeAll) {
                Text("See All")
            }
        }
        content()
    }
}

@Composable
private fun BookCard(
    book: Book,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Column {
            AsyncImage(
                model = book.thumbUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.padding(8.dp)) {
                if (book.seriesTitle.isNotBlank()) {
                    Text(
                        text = book.seriesTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeriesCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Column {
            AsyncImage(
                model = series.thumbUrl,
                contentDescription = series.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
