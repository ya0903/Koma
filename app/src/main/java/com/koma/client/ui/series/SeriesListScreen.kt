package com.koma.client.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Series
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.SeriesFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SeriesListUiState {
    data object Loading : SeriesListUiState
    data class Loaded(val series: List<Series>, val filter: SeriesFilter) : SeriesListUiState
    data class Error(val message: String) : SeriesListUiState
}

@HiltViewModel
class SeriesListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
) : ViewModel() {

    private val libraryId: String = savedStateHandle.get<String>("libraryId")?.takeIf { it != "all" } ?: ""
    private val _state = MutableStateFlow<SeriesListUiState>(SeriesListUiState.Loading)
    val state: StateFlow<SeriesListUiState> = _state.asStateFlow()
    private var currentFilter = SeriesFilter()

    init { load() }

    fun setFilter(filter: SeriesFilter) {
        currentFilter = filter
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = SeriesListUiState.Loading
            try {
                val server = serverRepo.getActive() ?: run {
                    _state.value = SeriesListUiState.Error("No active server")
                    return@launch
                }
                val mediaServer = registry.get(server)
                mediaServer.series(libraryId, currentFilter).collect { list ->
                    _state.value = SeriesListUiState.Loaded(list, currentFilter)
                }
            } catch (e: Exception) {
                _state.value = SeriesListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesListScreen(
    onSeriesClick: (seriesId: String) -> Unit,
    viewModel: SeriesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    // Grid size: 0 = small (100dp), 1 = medium (120dp), 2 = large (160dp)
    var gridSize by remember { mutableIntStateOf(1) }
    val gridMinWidth = when (gridSize) {
        0 -> 100.dp
        2 -> 160.dp
        else -> 120.dp
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Series") },
                actions = {
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { gridSize = (gridSize + 1) % 3 }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Grid size")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            if (isSearchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search series...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Filter chips
            if (state is SeriesListUiState.Loaded) {
                val current = (state as SeriesListUiState.Loaded).filter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeriesFilter.ReadStatus.entries.forEach { status ->
                        FilterChip(
                            selected = current.readStatus == status,
                            onClick = { viewModel.setFilter(current.copy(readStatus = status)) },
                            label = {
                                Text(
                                    when (status) {
                                        SeriesFilter.ReadStatus.ALL -> "All"
                                        SeriesFilter.ReadStatus.UNREAD -> "Unread"
                                        SeriesFilter.ReadStatus.IN_PROGRESS -> "Reading"
                                        SeriesFilter.ReadStatus.COMPLETED -> "Read"
                                    }
                                )
                            },
                        )
                    }
                }
            }

            when (val s = state) {
                SeriesListUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is SeriesListUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is SeriesListUiState.Loaded -> {
                    val filtered = if (searchQuery.isBlank()) s.series
                        else s.series.filter { it.title.contains(searchQuery, ignoreCase = true) }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(gridMinWidth),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filtered, key = { it.id }) { series ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeriesClick(series.id) },
                            ) {
                                Column {
                                    AsyncImage(
                                        model = series.thumbUrl,
                                        contentDescription = series.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f),
                                    )
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            series.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                        )
                                        if (series.unreadCount > 0) {
                                            Badge { Text("${series.unreadCount}") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
