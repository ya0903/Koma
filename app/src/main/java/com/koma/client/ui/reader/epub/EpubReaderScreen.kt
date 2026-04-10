package com.koma.client.ui.reader.epub

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import com.koma.client.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.koma.client.data.reader.EpubBook
import com.koma.client.data.reader.EpubChapter
import com.koma.client.data.reader.EpubDownloader
import com.koma.client.data.reader.EpubParser
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Bookmark
import com.koma.client.domain.model.DownloadState
import com.koma.client.domain.repo.BookmarkRepository
import com.koma.client.domain.repo.DownloadRepository
import com.koma.client.domain.repo.ReadProgressRepository
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.ui.reader.common.EpubFontFamily
import com.koma.client.ui.reader.common.EpubTheme
import com.koma.client.ui.reader.common.ReaderPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import kotlin.math.roundToInt

data class EpubReaderUiState(
    val loading: Boolean = true,
    val downloading: Boolean = false,
    val bookTitle: String = "",
    val chapters: List<EpubChapter> = emptyList(),
    val currentChapter: Int = 0,
    val overlayVisible: Boolean = false,
    val showSettings: Boolean = false,
    val showChapterList: Boolean = false,
    val showBookmarks: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val error: String? = null,
    // Settings
    val fontFamily: EpubFontFamily = EpubFontFamily.SYSTEM,
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val margin: Int = 16,
    val theme: EpubTheme = EpubTheme.LIGHT,
    val justified: Boolean = true,
    val overridePublisherFont: Boolean = false,
    // Internal
    val epubBook: EpubBook? = null,
    val scrollProgress: Float = 0f,
)

@HiltViewModel
class EpubReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
    private val downloader: EpubDownloader,
    private val parser: EpubParser,
    private val progressRepo: ReadProgressRepository,
    private val readerPrefs: ReaderPreferences,
    private val bookmarkRepo: BookmarkRepository,
    private val downloadRepo: DownloadRepository,
) : ViewModel() {

    val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _uiState = MutableStateFlow(EpubReaderUiState())
    val uiState: StateFlow<EpubReaderUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        loadBook()
        observePreferences()
        observeBookmarks()
    }

    private fun loadBook() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(downloading = true) }
                val server = serverRepo.getActive() ?: throw Exception("No active server")
                val mediaServer = registry.get(server)
                val book = mediaServer.book(bookId)

                // Check if book is already downloaded locally
                val localDownload = downloadRepo.getByBookId(bookId)
                val epubFile = if (localDownload != null &&
                    localDownload.state == DownloadState.COMPLETE &&
                    localDownload.filePath != null
                ) {
                    // Use already-downloaded file
                    java.io.File(localDownload.filePath)
                } else {
                    // Download from network
                    val fileUrl = mediaServer.fileUrl(bookId)
                    downloader.download(bookId, fileUrl)
                }
                _uiState.update { it.copy(downloading = false, bookTitle = book.title) }

                // Parse the EPUB
                val epubBook = parser.parse(epubFile, bookId)

                // Restore progress
                val savedProgress = progressRepo.get(bookId)
                val startChapter = savedProgress?.page?.coerceIn(0, (epubBook.chapters.size - 1).coerceAtLeast(0)) ?: 0

                _uiState.update {
                    it.copy(
                        loading = false,
                        bookTitle = epubBook.title.ifBlank { book.title },
                        chapters = epubBook.chapters,
                        currentChapter = startChapter,
                        epubBook = epubBook,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, downloading = false, error = e.message) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch { readerPrefs.epubFontFamily.collect { v -> _uiState.update { it.copy(fontFamily = v) } } }
        viewModelScope.launch { readerPrefs.epubFontSize.collect { v -> _uiState.update { it.copy(fontSize = v) } } }
        viewModelScope.launch { readerPrefs.epubLineHeight.collect { v -> _uiState.update { it.copy(lineHeight = v) } } }
        viewModelScope.launch { readerPrefs.epubMargin.collect { v -> _uiState.update { it.copy(margin = v) } } }
        viewModelScope.launch { readerPrefs.epubTheme.collect { v -> _uiState.update { it.copy(theme = v) } } }
        viewModelScope.launch { readerPrefs.epubJustified.collect { v -> _uiState.update { it.copy(justified = v) } } }
        viewModelScope.launch { readerPrefs.epubOverridePublisherFont.collect { v -> _uiState.update { it.copy(overridePublisherFont = v) } } }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            bookmarkRepo.getByBookId(bookId).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    fun goToChapter(index: Int) {
        val clamped = index.coerceIn(0, (_uiState.value.chapters.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentChapter = clamped, showChapterList = false) }
        debounceSaveProgress(clamped)
    }

    fun nextChapter() {
        val current = _uiState.value.currentChapter
        if (current < _uiState.value.chapters.size - 1) {
            goToChapter(current + 1)
        }
    }

    fun previousChapter() {
        val current = _uiState.value.currentChapter
        if (current > 0) {
            goToChapter(current - 1)
        }
    }

    fun toggleOverlay() {
        _uiState.update { it.copy(overlayVisible = !it.overlayVisible) }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun showChapterList() {
        _uiState.update { it.copy(showChapterList = true) }
    }

    fun hideChapterList() {
        _uiState.update { it.copy(showChapterList = false) }
    }

    fun showBookmarks() {
        _uiState.update { it.copy(showBookmarks = true) }
    }

    fun hideBookmarks() {
        _uiState.update { it.copy(showBookmarks = false) }
    }

    fun addBookmark(note: String? = null) {
        viewModelScope.launch {
            bookmarkRepo.add(
                bookId = bookId,
                page = _uiState.value.currentChapter,
                locator = _uiState.value.chapters.getOrNull(_uiState.value.currentChapter)?.title,
                note = note,
            )
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch {
            bookmarkRepo.delete(id)
        }
    }

    fun updateScrollProgress(progress: Float) {
        _uiState.update { it.copy(scrollProgress = progress) }
    }

    fun setFontFamily(family: EpubFontFamily) {
        _uiState.update { it.copy(fontFamily = family) }
        viewModelScope.launch { readerPrefs.setEpubFontFamily(family) }
    }

    fun setFontSize(size: Int) {
        _uiState.update { it.copy(fontSize = size) }
        viewModelScope.launch { readerPrefs.setEpubFontSize(size) }
    }

    fun setLineHeight(height: Float) {
        _uiState.update { it.copy(lineHeight = height) }
        viewModelScope.launch { readerPrefs.setEpubLineHeight(height) }
    }

    fun setMargin(margin: Int) {
        _uiState.update { it.copy(margin = margin) }
        viewModelScope.launch { readerPrefs.setEpubMargin(margin) }
    }

    fun setTheme(theme: EpubTheme) {
        _uiState.update { it.copy(theme = theme) }
        viewModelScope.launch { readerPrefs.setEpubTheme(theme) }
    }

    fun setJustified(justified: Boolean) {
        _uiState.update { it.copy(justified = justified) }
        viewModelScope.launch { readerPrefs.setEpubJustified(justified) }
    }

    fun setOverridePublisherFont(override: Boolean) {
        _uiState.update { it.copy(overridePublisherFont = override) }
        viewModelScope.launch { readerPrefs.setEpubOverridePublisherFont(override) }
    }

    private fun debounceSaveProgress(chapter: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val total = _uiState.value.chapters.size
            val completed = chapter >= total - 1
            progressRepo.save(bookId, chapter, completed)
        }
    }
}

/**
 * Generates the CSS for the EPUB WebView based on current settings.
 */
private fun buildCss(state: EpubReaderUiState): String {
    val fontImportant = if (state.overridePublisherFont) " !important" else ""
    return """
        html, body {
            background-color: ${state.theme.bgColor}$fontImportant;
            color: ${state.theme.textColor}$fontImportant;
            font-family: ${state.fontFamily.cssValue}$fontImportant;
            font-size: ${state.fontSize}px$fontImportant;
            line-height: ${state.lineHeight}$fontImportant;
            margin: 0;
            padding: ${state.margin}px;
            word-wrap: break-word;
            overflow-wrap: break-word;
            text-align: ${if (state.justified) "justify" else "start"}$fontImportant;
        }
        img {
            max-width: 100% !important;
            height: auto !important;
        }
        svg {
            max-width: 100% !important;
        }
        a {
            color: ${state.theme.textColor};
        }
        pre, code {
            white-space: pre-wrap;
            word-wrap: break-word;
        }
    """.trimIndent()
}

/**
 * Generates a wrapper HTML that loads the chapter content via an iframe
 * or injects the chapter XHTML with our CSS.
 */
private fun buildHtmlWrapper(chapterContent: String, css: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            <style>$css</style>
        </head>
        <body>
            $chapterContent
            <script>
                // Report scroll progress to Android
                function reportProgress() {
                    var scrollTop = document.documentElement.scrollTop || document.body.scrollTop;
                    var scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight;
                    var clientHeight = document.documentElement.clientHeight || document.body.clientHeight;
                    var progress = scrollHeight <= clientHeight ? 1.0 : scrollTop / (scrollHeight - clientHeight);
                    if (typeof Android !== 'undefined') {
                        Android.onScrollProgress(progress);
                    }
                }

                // Tap handler for overlay toggle
                document.addEventListener('click', function(e) {
                    // Don't toggle overlay on link clicks
                    if (e.target.tagName === 'A' || e.target.closest('a')) return;
                    if (typeof Android !== 'undefined') {
                        Android.onTap();
                    }
                });

                window.addEventListener('scroll', function() {
                    reportProgress();
                });

                // Initial progress report
                window.addEventListener('load', function() {
                    reportProgress();
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}

/**
 * JS interface for communication between WebView and Android.
 */
class EpubJsInterface(
    private val onTap: () -> Unit,
    private val onScrollProgress: (Float) -> Unit,
) {
    @JavascriptInterface
    fun onTap() {
        onTap.invoke()
    }

    @JavascriptInterface
    fun onScrollProgress(progress: Float) {
        onScrollProgress.invoke(progress)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpubReaderScreen(
    onBack: () -> Unit,
    viewModel: EpubReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Keep screen on
    LaunchedEffect(Unit) {
        view.keepScreenOn = true
    }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }

    if (state.loading || state.downloading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                if (state.downloading) {
                    Text(
                        "Downloading EPUB...",
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        return
    }

    state.error?.let { err ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(err, color = Color.White)
        }
        return
    }

    val epubBook = state.epubBook ?: return
    val bgColor = Color(android.graphics.Color.parseColor(state.theme.bgColor))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        // WebView for chapter content
        val currentChapter = state.currentChapter
        val css = buildCss(state)

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    addJavascriptInterface(
                        EpubJsInterface(
                            onTap = { viewModel.toggleOverlay() },
                            onScrollProgress = { viewModel.updateScrollProgress(it) },
                        ),
                        "Android",
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            // Serve local EPUB files
                            val url = request?.url?.toString() ?: return null
                            if (url.startsWith("file://")) {
                                val path = Uri.decode(url.removePrefix("file://"))
                                val file = File(path)
                                if (file.exists()) {
                                    val mimeType = when {
                                        path.endsWith(".css") -> "text/css"
                                        path.endsWith(".js") -> "application/javascript"
                                        path.endsWith(".png") -> "image/png"
                                        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                                        path.endsWith(".gif") -> "image/gif"
                                        path.endsWith(".svg") -> "image/svg+xml"
                                        path.endsWith(".woff") -> "font/woff"
                                        path.endsWith(".woff2") -> "font/woff2"
                                        path.endsWith(".ttf") -> "font/ttf"
                                        path.endsWith(".otf") -> "font/otf"
                                        else -> "application/octet-stream"
                                    }
                                    return WebResourceResponse(
                                        mimeType,
                                        "UTF-8",
                                        FileInputStream(file),
                                    )
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    webView = this
                }
            },
            update = { wv ->
                // Load the current chapter
                if (currentChapter in epubBook.chapters.indices) {
                    val chapter = epubBook.chapters[currentChapter]
                    val chapterFile = File(epubBook.extractDir, chapter.href)
                    if (chapterFile.exists()) {
                        val content = chapterFile.readText()
                        // Extract body content from the XHTML
                        val bodyContent = extractBodyContent(content)
                        val html = buildHtmlWrapper(bodyContent, css)
                        val baseUrl = "file://${chapterFile.parentFile?.absolutePath}/"
                        wv.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top overlay
        AnimatedVisibility(
            visible = state.overlayVisible,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.bookTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (state.chapters.isNotEmpty()) {
                            Text(
                                state.chapters[state.currentChapter].title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val isChapterBookmarked = state.bookmarks.any { it.page == state.currentChapter }
                    IconButton(onClick = { viewModel.showBookmarks() }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Bookmarks list")
                    }
                    IconButton(onClick = { viewModel.addBookmark() }) {
                        Icon(
                            ImageVector.vectorResource(
                                id = if (isChapterBookmarked) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark,
                            ),
                            contentDescription = if (isChapterBookmarked) "Remove bookmark" else "Add bookmark",
                            tint = if (isChapterBookmarked) Color(0xFFFFD700)
                                   else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { viewModel.showChapterList() }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Chapters")
                    }
                    IconButton(onClick = { viewModel.showSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
                modifier = Modifier.systemBarsPadding(),
            )
        }

        // Bottom overlay with chapter navigation
        AnimatedVisibility(
            visible = state.overlayVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .systemBarsPadding(),
            ) {
                // Chapter progress
                if (state.chapters.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.currentChapter + 1} / ${state.chapters.size}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Slider(
                        value = state.currentChapter.toFloat(),
                        onValueChange = { viewModel.goToChapter(it.roundToInt()) },
                        valueRange = 0f..(state.chapters.size - 1).toFloat().coerceAtLeast(0f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Previous / Next chapter buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { viewModel.previousChapter() },
                        enabled = state.currentChapter > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous chapter")
                    }
                    Text(
                        state.chapters.getOrNull(state.currentChapter)?.title ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = { viewModel.nextChapter() },
                        enabled = state.currentChapter < state.chapters.size - 1,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next chapter")
                    }
                }
            }
        }

        // Progress indicator at top
        if (state.chapters.size > 1) {
            LinearProgressIndicator(
                progress = {
                    if (state.chapters.isEmpty()) 0f
                    else (state.currentChapter + state.scrollProgress) / state.chapters.size
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }

    // Chapter list sheet
    if (state.showChapterList) {
        ChapterListSheet(
            chapters = state.chapters,
            currentChapter = state.currentChapter,
            onChapterSelect = { viewModel.goToChapter(it) },
            onDismiss = { viewModel.hideChapterList() },
        )
    }

    // Bookmark list sheet
    if (state.showBookmarks) {
        EpubBookmarkListSheet(
            bookmarks = state.bookmarks,
            chapters = state.chapters,
            onBookmarkClick = { bookmark ->
                bookmark.page?.let { viewModel.goToChapter(it) }
                viewModel.hideBookmarks()
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onDismiss = { viewModel.hideBookmarks() },
        )
    }

    // Settings sheet
    if (state.showSettings) {
        EpubSettingsSheet(
            fontFamily = state.fontFamily,
            fontSize = state.fontSize,
            lineHeight = state.lineHeight,
            margin = state.margin,
            theme = state.theme,
            justified = state.justified,
            overridePublisherFont = state.overridePublisherFont,
            onFontFamilyChange = viewModel::setFontFamily,
            onFontSizeChange = viewModel::setFontSize,
            onLineHeightChange = viewModel::setLineHeight,
            onMarginChange = viewModel::setMargin,
            onThemeChange = viewModel::setTheme,
            onJustifiedChange = viewModel::setJustified,
            onOverridePublisherFontChange = viewModel::setOverridePublisherFont,
            onDismiss = { viewModel.hideSettings() },
        )
    }
}

/**
 * Extracts the body content from an XHTML document.
 * Strips the <html>, <head>, and <body> tags, returning only the inner content.
 */
private fun extractBodyContent(xhtml: String): String {
    // Try to extract content between <body...> and </body>
    val bodyRegex = Regex("<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL)
    val match = bodyRegex.find(xhtml)
    return match?.groupValues?.get(1) ?: xhtml
}
