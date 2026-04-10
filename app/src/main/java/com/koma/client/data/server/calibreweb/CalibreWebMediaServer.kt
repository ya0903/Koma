package com.koma.client.data.server.calibreweb

import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.server.calibreweb.dto.CalibreBookDto
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.LibraryStats
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.model.Series
import com.koma.client.domain.model.ThumbKind
import com.koma.client.domain.server.MediaServer
import com.koma.client.domain.server.MediaServerType
import com.koma.client.domain.server.SearchFilter
import com.koma.client.domain.server.SearchResults
import com.koma.client.domain.server.SeriesFilter
import com.koma.client.domain.server.ServerCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * [MediaServer] implementation for Calibre-Web.
 *
 * Calibre-Web is a flat library — there are no native series or library groupings.
 * This implementation synthesises virtual [Series] entries from the `series` metadata
 * field on each book. Books without a series go into the "Uncategorized" virtual series.
 *
 * Auth: HTTP Basic only (v1). If Basic auth fails the user is prompted to enable it
 * in their Calibre-Web admin settings. Full session-cookie auth can be a follow-up.
 *
 * Progress sync: Calibre-Web has no stable API for per-page progress. Progress is
 * LOCAL ONLY — see [ServerCapabilities.calibreWebDefaults] (serverProgressSync = false).
 */
class CalibreWebMediaServer(
    override val id: String,
    private val baseUrl: String,
    private val credentialStore: CredentialStore,
    private val baseOkHttpClient: OkHttpClient,
    private val json: Json,
) : MediaServer {

    override val type: MediaServerType = MediaServerType.CALIBRE_WEB
    override val capabilities: ServerCapabilities = ServerCapabilities.calibreWebDefaults()

    // ─── HTTP / Retrofit setup ────────────────────────────────────────────────

    private val okHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val username = credentialStore.getUsername(id) ?: ""
                val password = credentialStore.getPassword(id) ?: ""
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val api: CalibreWebApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CalibreWebApi::class.java)
    }

    /** Exposes the authenticated OkHttpClient for use by Coil image loading. */
    fun authenticatedClient(): OkHttpClient = okHttpClient

    // ─── Core API ─────────────────────────────────────────────────────────────

    override suspend fun authenticate(): Result<Unit> = runCatching {
        // Attempt a minimal list-books call; a 401 throws an HttpException which
        // propagates as a failure result to the caller.
        api.checkAuth(limit = 1)
        Unit
    }

    override fun libraries(): Flow<List<Library>> = flow {
        // Calibre-Web has a single flat library; emit a synthetic entry.
        emit(listOf(calibreLibrary(id)))
    }

    override fun series(libraryId: String, filter: SeriesFilter): Flow<List<Series>> = flow {
        val books = loadAllBooks()
        emit(groupBooksIntoSeries(books, id, baseUrl))
    }

    override suspend fun seriesDetail(seriesId: String): Series {
        val books = loadAllBooks()
        val seriesList = groupBooksIntoSeries(books, id, baseUrl)
        return seriesList.firstOrNull { it.id == seriesId }
            ?: Series(
                id = seriesId,
                serverId = id,
                libraryId = CALIBRE_LIBRARY_ID,
                title = CALIBRE_UNCATEGORIZED_NAME,
            )
    }

    override fun books(seriesId: String): Flow<List<Book>> = flow {
        val allBooks = loadAllBooks()
        val filtered = filterBooksForSeries(allBooks, seriesId)
        val domainBooks = filtered
            .sortedWith(compareBy({ it.seriesIndex ?: Float.MAX_VALUE }, { it.title }))
            .map { it.toDomain(serverId = id, baseUrl = baseUrl, seriesId = seriesId) }
        emit(domainBooks)
    }

    override suspend fun book(bookId: String): Book {
        val intId = bookId.toIntOrNull() ?: throw IllegalArgumentException("Invalid book ID: $bookId")
        val dto = api.getBook(intId)
        return dto.toDomain(serverId = id, baseUrl = baseUrl)
    }

    // ─── URL helpers ──────────────────────────────────────────────────────────

    /**
     * Calibre-Web books are typically EPUBs or PDFs — not page-image–based.
     * Page URLs are not applicable; return empty string.
     */
    override suspend fun pageUrl(bookId: String, page: Int): String = ""

    /**
     * Returns the OPDS download URL for the book, selecting the best available format.
     * Falls back to EPUB, then PDF, then CBZ.
     */
    override suspend fun fileUrl(bookId: String): String {
        val intId = bookId.toIntOrNull() ?: throw IllegalArgumentException("Invalid book ID: $bookId")
        val dto = runCatching { api.getBook(intId) }.getOrNull()
        val formats = dto?.let { parseFormats(it.formats) } ?: emptyList()
        val format = pickBestFormat(formats) ?: "epub"
        return calibreDownloadUrl(baseUrl, intId, format)
    }

    override suspend fun thumbnailUrl(id: String, kind: ThumbKind): String {
        // Calibre-Web uses /cover/{book_id} for all thumbnails
        return "${baseUrl.trimEnd('/')}/cover/$id"
    }

    // ─── Progress ─────────────────────────────────────────────────────────────

    /**
     * No-op: Calibre-Web does not have a stable API for server-side progress sync.
     * Progress is stored locally only (capability flag serverProgressSync = false).
     */
    override suspend fun updateProgress(bookId: String, progress: ReadProgress) {
        // intentional no-op
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    override suspend fun search(query: String, filter: SearchFilter): SearchResults {
        val result = api.search(query)
        val books = result.rows.map { it.toDomain(serverId = id, baseUrl = baseUrl) }
        // Derive virtual series from search results
        val series = groupBooksIntoSeries(result.rows, id, baseUrl)
        return SearchResults(series = series, books = books)
    }

    // ─── Dashboard ────────────────────────────────────────────────────────────

    override suspend fun onDeckBooks(libraryId: String?): List<Book> {
        // Calibre-Web has no on-deck concept; return empty
        return emptyList()
    }

    override suspend fun recentlyAddedSeries(libraryId: String?): List<Series> {
        // Calibre-Web has a single flat library; libraryId is ignored
        val books = api.listBooks(limit = 100, sort = "timestamp", order = "desc").rows
        return groupBooksIntoSeries(books, id, baseUrl)
    }

    override suspend fun recentlyUpdatedSeries(libraryId: String?): List<Series> {
        // Calibre-Web has a single flat library; libraryId is ignored
        val books = api.listBooks(limit = 100, sort = "last_modified", order = "desc").rows
        return groupBooksIntoSeries(books, id, baseUrl)
    }

    override suspend fun recentlyAddedBooks(libraryId: String?): List<Book> {
        // Calibre-Web has a single flat library; libraryId is ignored
        val rows = api.listBooks(limit = 20, sort = "timestamp", order = "desc").rows
        return rows.map { it.toDomain(serverId = id, baseUrl = baseUrl) }
    }

    override suspend fun recentlyReleasedBooks(libraryId: String?): List<Book> {
        // Calibre-Web has a single flat library; libraryId is ignored
        val rows = api.listBooks(limit = 20, sort = "pubdate", order = "desc").rows
        return rows.map { it.toDomain(serverId = id, baseUrl = baseUrl) }
    }

    override suspend fun recentlyReadBooks(libraryId: String?): List<Book> {
        // No read-progress tracking on server; return empty
        return emptyList()
    }

    // ─── Stats + metadata ─────────────────────────────────────────────────────

    override suspend fun libraryStats(libraryId: String?): LibraryStats {
        val response = api.listBooks(limit = 1)
        val total = response.totalBooks.toLong()
        val seriesCount = runCatching {
            val allBooks = loadAllBooks()
            groupBooksIntoSeries(allBooks, id, baseUrl).size.toLong()
        }.getOrDefault(0L)
        return LibraryStats(
            seriesCount = seriesCount,
            bookCount = total,
        )
    }

    override suspend fun availableGenres(): List<String> = emptyList()

    override suspend fun availableTags(): List<String> = emptyList()

    override suspend fun availablePublishers(): List<String> = emptyList()

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Loads all books from Calibre-Web in a single paginated batch.
     * Uses a large limit (500) since Calibre-Web libraries are typically small.
     * For very large libraries, pagination would be needed.
     */
    private suspend fun loadAllBooks(): List<CalibreBookDto> {
        val response = api.listBooks(offset = 0, limit = 500, sort = "timestamp", order = "desc")
        return response.rows
    }
}
