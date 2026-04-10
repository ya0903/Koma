package com.koma.client.data.server.komga

import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.server.komga.dto.KomgaReadProgressUpdateDto
import com.koma.client.di.BaseOkHttpClient
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

class KomgaMediaServer(
    override val id: String,
    private val baseUrl: String,
    private val credentialStore: CredentialStore,
    private val baseOkHttpClient: OkHttpClient,
    private val json: Json,
) : MediaServer {

    override val type: MediaServerType = MediaServerType.KOMGA
    override val capabilities: ServerCapabilities = ServerCapabilities.komgaDefaults()

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

    private val api: KomgaApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KomgaApi::class.java)
    }

    /** Authenticated OkHttpClient for Coil image loading */
    fun authenticatedClient(): OkHttpClient = okHttpClient

    override suspend fun authenticate(): Result<Unit> = runCatching {
        api.getMe()
        Unit
    }

    override fun libraries(): Flow<List<Library>> = flow {
        val libs = api.getLibraries().map { it.toDomain(id) }
        emit(libs)
    }

    override fun series(libraryId: String, filter: SeriesFilter): Flow<List<Series>> = flow {
        val sort = when (filter.sortBy) {
            com.koma.client.domain.server.SeriesSort.TITLE -> "metadata.titleSort,asc"
            com.koma.client.domain.server.SeriesSort.DATE_ADDED -> "created,desc"
            com.koma.client.domain.server.SeriesSort.LAST_READ -> "lastModified,desc"
        }
        val readStatus = when (filter.readStatus) {
            SeriesFilter.ReadStatus.ALL -> null
            SeriesFilter.ReadStatus.UNREAD -> "UNREAD"
            SeriesFilter.ReadStatus.IN_PROGRESS -> "IN_PROGRESS"
            SeriesFilter.ReadStatus.COMPLETED -> "READ"
        }
        // Load first page; pagination support via Paging3 is deferred
        val page = api.getSeries(
            libraryId = libraryId,
            readStatus = readStatus,
            sort = sort,
            page = 0,
            size = 500,
        )
        emit(page.content.map { it.toDomain(id, baseUrl) })
    }

    override suspend fun seriesDetail(id: String): Series {
        return api.getSeriesDetail(id).toDomain(this.id, baseUrl)
    }

    override fun books(seriesId: String): Flow<List<Book>> = flow {
        val page = api.getSeriesBooks(seriesId)
        emit(page.content.map { it.toDomain(id, baseUrl) })
    }

    override suspend fun book(id: String): Book {
        return api.getBook(id).toDomain(this.id, baseUrl)
    }

    override suspend fun pageUrl(bookId: String, page: Int): String =
        "${baseUrl.trimEnd('/')}/api/v1/books/$bookId/pages/${page + 1}"

    override suspend fun fileUrl(bookId: String): String =
        "${baseUrl.trimEnd('/')}/api/v1/books/$bookId/file"

    override suspend fun thumbnailUrl(id: String, kind: ThumbKind): String {
        val segment = when (kind) {
            ThumbKind.SERIES -> "series"
            ThumbKind.BOOK -> "books"
            ThumbKind.LIBRARY -> "libraries"
        }
        return "${baseUrl.trimEnd('/')}/api/v1/$segment/$id/thumbnail"
    }

    override suspend fun updateProgress(bookId: String, progress: ReadProgress) {
        api.updateReadProgress(
            bookId,
            KomgaReadProgressUpdateDto(
                page = progress.page,
                completed = progress.completed,
            ),
        )
    }

    override suspend fun search(query: String, filter: SearchFilter): SearchResults {
        // FIXME: Use Komga's actual search endpoint (POST /api/v1/series/list with SearchRequestDto)
        //  for proper full-text search. Current client-side filtering is a placeholder.
        val series = api.getSeries(page = 0, size = 50).content
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toDomain(id, baseUrl) }
        return SearchResults(series = series, books = emptyList())
    }

    override suspend fun onDeckBooks(libraryId: String?): List<Book> {
        return api.getOnDeck(libraryId = libraryId, size = 20).content.map { it.toDomain(id, baseUrl) }
    }

    override suspend fun recentlyAddedSeries(libraryId: String?): List<Series> {
        return api.getNewSeries(libraryId = libraryId, size = 20).content.map { it.toDomain(id, baseUrl) }
    }

    override suspend fun recentlyUpdatedSeries(libraryId: String?): List<Series> {
        return api.getSeries(libraryId = libraryId, sort = "lastModified,desc", size = 20).content.map { it.toDomain(id, baseUrl) }
    }

    override suspend fun recentlyAddedBooks(libraryId: String?): List<Book> {
        return api.getBooks(libraryId = libraryId, sort = "created,desc", size = 20).content.map { it.toDomain(id, baseUrl) }
    }

    override suspend fun recentlyReleasedBooks(libraryId: String?): List<Book> {
        return api.getBooks(libraryId = libraryId, sort = "metadata.releaseDate,desc", size = 20).content.map { it.toDomain(id, baseUrl) }
    }

    override suspend fun recentlyReadBooks(libraryId: String?): List<Book> {
        return api.getBooks(libraryId = libraryId, readStatus = "READ", sort = "readProgress.readDate,desc", size = 20).content.map { it.toDomain(id, baseUrl) }
    }

    override suspend fun libraryStats(libraryId: String?): LibraryStats {
        val seriesPage = api.getSeries(libraryId = libraryId, page = 0, size = 0)
        val booksPage = api.getBooks(libraryId = libraryId, page = 0, size = 0)
        return LibraryStats(
            seriesCount = seriesPage.totalElements,
            bookCount = booksPage.totalElements,
        )
    }

    override suspend fun availableGenres(): List<String> =
        runCatching { api.getGenres() }.getOrDefault(emptyList())

    override suspend fun availableTags(): List<String> =
        runCatching { api.getSeriesTags() }.getOrDefault(emptyList())

    override suspend fun availablePublishers(): List<String> =
        runCatching { api.getPublishers() }.getOrDefault(emptyList())
}
