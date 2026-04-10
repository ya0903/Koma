package com.koma.client.data.server.kavita

import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.server.kavita.dto.KavitaLoginRequest
import com.koma.client.data.server.kavita.dto.KavitaProgressDto
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class KavitaMediaServer(
    override val id: String,
    private val baseUrl: String,
    private val credentialStore: CredentialStore,
    private val baseOkHttpClient: OkHttpClient,
    private val json: Json,
) : MediaServer {

    override val type: MediaServerType = MediaServerType.KAVITA
    override val capabilities: ServerCapabilities = ServerCapabilities.kavitaDefaults()

    private val okHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val token = credentialStore.getJwtToken(id)
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
    }

    private val api: KavitaApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KavitaApi::class.java)
    }

    fun authenticatedClient(): OkHttpClient = okHttpClient

    override suspend fun authenticate(): Result<Unit> = try {
        val username = credentialStore.getUsername(id) ?: ""
        val password = credentialStore.getPassword(id) ?: ""
        val response = api.login(KavitaLoginRequest(username, password))
        credentialStore.storeJwtToken(id, response.token)
        Result.success(Unit)
    } catch (e: retrofit2.HttpException) {
        Result.failure(Exception("Kavita login failed (HTTP ${e.code()}): ${e.message()}. Check your credentials and server URL."))
    } catch (e: Exception) {
        Result.failure(Exception("Kavita connection failed: ${e.message}"))
    }

    override fun libraries(): Flow<List<Library>> = flow {
        val libs = api.getLibraries().map { it.toDomain(id) }
        emit(libs)
    }

    override fun series(libraryId: String, filter: SeriesFilter): Flow<List<Series>> = flow {
        val libId = libraryId.toIntOrNull()
        val seriesList = api.getSeries(libraryId = libId).map { it.toDomain(id, baseUrl) }
        emit(seriesList)
    }

    override suspend fun seriesDetail(id: String): Series {
        val intId = id.toIntOrNull() ?: throw IllegalArgumentException("Invalid series ID: $id")
        return api.getSeriesDetail(intId).toDomain(this.id, baseUrl)
    }

    override fun books(seriesId: String): Flow<List<Book>> = flow {
        val intSeriesId = seriesId.toIntOrNull() ?: throw IllegalArgumentException("Invalid series ID: $seriesId")
        val volumes = api.getVolumes(intSeriesId)
        val seriesTitle = runCatching {
            api.getSeriesDetail(intSeriesId).let { s ->
                s.localizedName.ifBlank { s.name }
            }
        }.getOrDefault("")
        val books = volumes.flatMap { volume ->
            volume.chapters.map { chapter ->
                chapter.toDomain(
                    serverId = id,
                    baseUrl = baseUrl,
                    seriesId = intSeriesId,
                    seriesTitle = seriesTitle,
                )
            }
        }
        emit(books)
    }

    override suspend fun book(id: String): Book {
        val intId = id.toIntOrNull() ?: throw IllegalArgumentException("Invalid book ID: $id")
        val chapter = api.getChapter(intId)
        return chapter.toDomain(
            serverId = this.id,
            baseUrl = baseUrl,
            seriesId = chapter.seriesId,
        )
    }

    /**
     * Kavita pages are 0-indexed: page=0 is the first page.
     * The [page] parameter from our domain is also 0-indexed, so pass directly.
     */
    override suspend fun pageUrl(bookId: String, page: Int): String =
        "${baseUrl.trimEnd('/')}/api/Reader/image?chapterId=$bookId&page=$page"

    override suspend fun fileUrl(bookId: String): String =
        "${baseUrl.trimEnd('/')}/api/Reader/file?chapterId=$bookId"

    override suspend fun thumbnailUrl(id: String, kind: ThumbKind): String {
        val base = baseUrl.trimEnd('/')
        return when (kind) {
            ThumbKind.SERIES -> "$base/api/Image/series-cover?seriesId=$id"
            ThumbKind.BOOK -> "$base/api/Image/chapter-cover?chapterId=$id"
            ThumbKind.LIBRARY -> "$base/api/Image/library-cover?libraryId=$id"
        }
    }

    override suspend fun updateProgress(bookId: String, progress: ReadProgress) {
        val chapterId = bookId.toIntOrNull() ?: return
        // Fetch chapter to get volumeId/seriesId/libraryId context
        val chapter = runCatching { api.getChapter(chapterId) }.getOrNull() ?: return
        api.updateProgress(
            KavitaProgressDto(
                volumeId = chapter.volumeId,
                chapterId = chapterId,
                pageNum = progress.page,
                seriesId = chapter.seriesId,
                libraryId = 0, // Kavita resolves libraryId server-side from seriesId
            ),
        )
    }

    override suspend fun search(query: String, filter: SearchFilter): SearchResults {
        val result = api.search(query)
        val series = result.series.map { sr ->
            Series(
                id = sr.seriesId.toString(),
                serverId = id,
                libraryId = sr.libraryId.toString(),
                title = sr.localizedName.ifBlank { sr.name },
                thumbUrl = "${baseUrl.trimEnd('/')}/api/Image/series-cover?seriesId=${sr.seriesId}",
            )
        }
        val books = result.chapters.map { ch ->
            ch.toDomain(
                serverId = id,
                baseUrl = baseUrl,
                seriesId = ch.seriesId,
            )
        }
        return SearchResults(series = series, books = books)
    }

    override suspend fun onDeckBooks(): List<Book> {
        // Kavita doesn't have a direct on-deck endpoint; return empty for now
        return emptyList()
    }

    override suspend fun recentlyAddedSeries(): List<Series> {
        return api.getSeries(size = 20).map { it.toDomain(id, baseUrl) }
    }

    override suspend fun recentlyUpdatedSeries(): List<Series> {
        return api.getSeries(size = 20).map { it.toDomain(id, baseUrl) }
    }

    override suspend fun recentlyAddedBooks(): List<Book> = emptyList()

    override suspend fun recentlyReleasedBooks(): List<Book> = emptyList()

    override suspend fun recentlyReadBooks(): List<Book> = emptyList()

    override suspend fun libraryStats(libraryId: String?): LibraryStats {
        val libId = libraryId?.toIntOrNull()
        val count = api.getSeries(libraryId = libId, size = 500).size
        return LibraryStats(seriesCount = count.toLong(), bookCount = 0L)
    }

    override suspend fun availableGenres(): List<String> = emptyList()

    override suspend fun availableTags(): List<String> = emptyList()

    override suspend fun availablePublishers(): List<String> = emptyList()
}
