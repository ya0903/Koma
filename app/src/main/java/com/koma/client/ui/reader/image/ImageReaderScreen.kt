package com.koma.client.ui.reader.image

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Bookmark
import com.koma.client.domain.repo.BookmarkRepository
import com.koma.client.domain.repo.ReadProgressRepository
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.ui.reader.common.FitMode
import com.koma.client.ui.reader.common.PageLayout
import com.koma.client.ui.reader.common.ReaderPreferences
import com.koma.client.ui.reader.common.ReadingDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageReaderUiState(
    val loading: Boolean = true,
    val bookTitle: String = "",
    val pageUrls: List<String> = emptyList(),
    val currentPage: Int = 0,
    val overlayVisible: Boolean = false,
    val fitMode: FitMode = FitMode.WIDTH,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val pageLayout: PageLayout = PageLayout.SINGLE,
    val keepScreenOn: Boolean = true,
    val showSettings: Boolean = false,
    val showBookmarks: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val brightness: Float = -1f, // -1 = system default
    val error: String? = null,
)

/**
 * Non-Hilt constructor for unit testing — takes pre-resolved data.
 * The @HiltViewModel version below delegates to this after resolving the book.
 */
class ImageReaderViewModel(
    private val bookId: String,
    pageUrls: List<String>,
    bookTitle: String,
    private val progressRepo: ReadProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ImageReaderUiState(
            loading = false,
            bookTitle = bookTitle,
            pageUrls = pageUrls,
        )
    )
    val uiState: StateFlow<ImageReaderUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (_uiState.value.pageUrls.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentPage = clamped) }
        debounceSaveProgress(clamped)
    }

    fun toggleOverlay() {
        _uiState.update { it.copy(overlayVisible = !it.overlayVisible) }
    }

    fun setFitMode(mode: FitMode) {
        _uiState.update { it.copy(fitMode = mode) }
    }

    fun setReadingDirection(dir: ReadingDirection) {
        _uiState.update { it.copy(readingDirection = dir) }
    }

    fun setPageLayout(layout: PageLayout) {
        _uiState.update { it.copy(pageLayout = layout) }
    }

    fun setKeepScreenOn(on: Boolean) {
        _uiState.update { it.copy(keepScreenOn = on) }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    private fun debounceSaveProgress(page: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val total = _uiState.value.pageUrls.size
            val completed = page >= total - 1
            progressRepo.save(bookId, page, completed)
        }
    }
}

/**
 * Hilt-injected ViewModel that resolves the book from SavedStateHandle,
 * fetches page URLs from the MediaServer, and delegates to the core ViewModel logic.
 */
@HiltViewModel
class HiltImageReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
    private val progressRepo: ReadProgressRepository,
    private val readerPrefs: ReaderPreferences,
    private val bookmarkRepo: BookmarkRepository,
) : ViewModel() {

    private val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _uiState = MutableStateFlow(ImageReaderUiState())
    val uiState: StateFlow<ImageReaderUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        loadBook()
        observePreferences()
        observeBookmarks()
    }

    private fun loadBook() {
        viewModelScope.launch {
            try {
                val server = serverRepo.getActive() ?: return@launch
                val mediaServer = registry.get(server)
                val book = mediaServer.book(bookId)
                val urls = (0 until book.pageCount).map { page ->
                    mediaServer.pageUrl(bookId, page)
                }
                val savedProgress = progressRepo.get(bookId)
                _uiState.update {
                    it.copy(
                        loading = false,
                        bookTitle = book.title,
                        pageUrls = urls,
                        currentPage = savedProgress?.page ?: 0,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            readerPrefs.fitMode.collect { mode -> _uiState.update { it.copy(fitMode = mode) } }
        }
        viewModelScope.launch {
            readerPrefs.readingDirection.collect { dir -> _uiState.update { it.copy(readingDirection = dir) } }
        }
        viewModelScope.launch {
            readerPrefs.pageLayout.collect { layout -> _uiState.update { it.copy(pageLayout = layout) } }
        }
        viewModelScope.launch {
            readerPrefs.keepScreenOn.collect { on -> _uiState.update { it.copy(keepScreenOn = on) } }
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            bookmarkRepo.getByBookId(bookId).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (_uiState.value.pageUrls.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentPage = clamped) }
        debounceSaveProgress(clamped)
    }

    fun toggleOverlay() = _uiState.update { it.copy(overlayVisible = !it.overlayVisible) }
    fun showSettings() = _uiState.update { it.copy(showSettings = true) }
    fun hideSettings() = _uiState.update { it.copy(showSettings = false) }
    fun showBookmarks() = _uiState.update { it.copy(showBookmarks = true) }
    fun hideBookmarks() = _uiState.update { it.copy(showBookmarks = false) }

    fun addBookmark(note: String? = null) {
        viewModelScope.launch {
            bookmarkRepo.add(
                bookId = bookId,
                page = _uiState.value.currentPage,
                locator = null,
                note = note,
            )
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch {
            bookmarkRepo.delete(id)
        }
    }

    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness) }
    }

    fun setFitMode(mode: FitMode) {
        _uiState.update { it.copy(fitMode = mode) }
        viewModelScope.launch { readerPrefs.setFitMode(mode) }
    }

    fun setReadingDirection(dir: ReadingDirection) {
        _uiState.update { it.copy(readingDirection = dir) }
        viewModelScope.launch { readerPrefs.setReadingDirection(dir) }
    }

    fun setPageLayout(layout: PageLayout) {
        _uiState.update { it.copy(pageLayout = layout) }
        viewModelScope.launch { readerPrefs.setPageLayout(layout) }
    }

    fun setKeepScreenOn(on: Boolean) {
        _uiState.update { it.copy(keepScreenOn = on) }
        viewModelScope.launch { readerPrefs.setKeepScreenOn(on) }
    }

    private fun debounceSaveProgress(page: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val total = _uiState.value.pageUrls.size
            progressRepo.save(bookId, page, page >= total - 1)
        }
    }
}

@Composable
fun ImageReaderScreen(
    onBack: () -> Unit,
    viewModel: HiltImageReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Keep screen on
    val view = LocalView.current
    LaunchedEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
    }

    // Brightness control — only affects this window, resets on exit
    val activity = LocalContext.current as? Activity
    LaunchedEffect(state.brightness) {
        activity?.window?.let { window ->
            val attrs = window.attributes
            attrs.screenBrightness = state.brightness
            window.attributes = attrs
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                val attrs = window.attributes
                attrs.screenBrightness = -1f // reset to system
                window.attributes = attrs
            }
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    state.error?.let { err ->
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(err, color = Color.White)
        }
        return
    }

    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.VolumeDown -> {
                            viewModel.goToPage(state.currentPage + 1)
                            true
                        }
                        Key.VolumeUp -> {
                            viewModel.goToPage(state.currentPage - 1)
                            true
                        }
                        else -> false
                    }
                } else false
            },
    ) {
        val direction = state.readingDirection

        when {
            direction.isContinuousScroll -> {
                // Webtoon mode: continuous vertical scroll — all pages in one LazyColumn
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = state.currentPage,
                )

                LaunchedEffect(listState.firstVisibleItemIndex) {
                    viewModel.goToPage(listState.firstVisibleItemIndex)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(state.pageUrls) { index, url ->
                        coil3.compose.SubcomposeAsyncImage(
                            model = url,
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { viewModel.toggleOverlay() })
                                },
                            loading = {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(400.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(color = Color.White) }
                            },
                            error = {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Failed to load", color = Color.Red) }
                            },
                        )
                    }
                }
            }
            direction.isHorizontal -> {
                val pagerState = rememberPagerState(
                    initialPage = state.currentPage,
                    pageCount = { state.pageUrls.size },
                )

                LaunchedEffect(pagerState.currentPage) {
                    viewModel.goToPage(pagerState.currentPage)
                }

                LaunchedEffect(state.currentPage) {
                    if (pagerState.currentPage != state.currentPage) {
                        pagerState.animateScrollToPage(state.currentPage)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    reverseLayout = direction.isReversed,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ReaderPage(
                        imageUrl = state.pageUrls[page],
                        fitMode = state.fitMode,
                        onTap = { viewModel.toggleOverlay() },
                    )
                }
            }
            else -> {
                // VERTICAL: page-by-page vertical pager
                val pagerState = rememberPagerState(
                    initialPage = state.currentPage,
                    pageCount = { state.pageUrls.size },
                )

                LaunchedEffect(pagerState.currentPage) {
                    viewModel.goToPage(pagerState.currentPage)
                }

                LaunchedEffect(state.currentPage) {
                    if (pagerState.currentPage != state.currentPage) {
                        pagerState.animateScrollToPage(state.currentPage)
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ReaderPage(
                        imageUrl = state.pageUrls[page],
                        fitMode = state.fitMode,
                        onTap = { viewModel.toggleOverlay() },
                    )
                }
            }
        }

        // Overlay
        ReaderOverlay(
            visible = state.overlayVisible,
            title = state.bookTitle,
            currentPage = state.currentPage,
            totalPages = state.pageUrls.size,
            bookmarks = state.bookmarks,
            brightness = state.brightness,
            onBack = onBack,
            onPageChange = { viewModel.goToPage(it) },
            onSettingsClick = { viewModel.showSettings() },
            onBookmarkClick = { viewModel.addBookmark() },
            onBookmarksListClick = { viewModel.showBookmarks() },
            onBrightnessChange = { viewModel.setBrightness(it) },
        )

        // Settings bottom sheet
        if (state.showSettings) {
            ReaderSettingsSheet(
                fitMode = state.fitMode,
                readingDirection = state.readingDirection,
                pageLayout = state.pageLayout,
                keepScreenOn = state.keepScreenOn,
                onFitModeChange = viewModel::setFitMode,
                onDirectionChange = viewModel::setReadingDirection,
                onLayoutChange = viewModel::setPageLayout,
                onKeepScreenOnChange = viewModel::setKeepScreenOn,
                onDismiss = { viewModel.hideSettings() },
            )
        }

        // Bookmark list bottom sheet
        if (state.showBookmarks) {
            BookmarkListSheet(
                bookmarks = state.bookmarks,
                onBookmarkClick = { bookmark ->
                    bookmark.page?.let { viewModel.goToPage(it) }
                    viewModel.hideBookmarks()
                },
                onDeleteBookmark = { viewModel.deleteBookmark(it) },
                onDismiss = { viewModel.hideBookmarks() },
            )
        }
    }

    // Request focus so volume key events are received
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
