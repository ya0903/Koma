package com.koma.client.domain.server

import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.LibraryStats
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.model.Series
import com.koma.client.domain.model.ThumbKind
import kotlinx.coroutines.flow.Flow

/**
 * Backend-agnostic media server facade. Concrete implementations:
 * - KomgaMediaServer (Plan 2)
 * - KavitaMediaServer (Plan 6)
 * - CalibreWebMediaServer (Plan 7)
 *
 * All IDs in the unified model are strings. Integer IDs from backends must be stringified.
 */
interface MediaServer {
    val id: String
    val type: MediaServerType
    val capabilities: ServerCapabilities

    suspend fun authenticate(): Result<Unit>

    fun libraries(): Flow<List<Library>>
    // TODO: Migrate to Flow<PagingData<Series>> when Paging3 is added (Plan 2).
    //  Current List return type is a Plan 1 simplification.
    fun series(libraryId: String, filter: SeriesFilter): Flow<List<Series>>
    suspend fun seriesDetail(id: String): Series
    fun books(seriesId: String): Flow<List<Book>>
    suspend fun book(id: String): Book

    suspend fun pageUrl(bookId: String, page: Int): String
    suspend fun fileUrl(bookId: String): String
    suspend fun thumbnailUrl(id: String, kind: ThumbKind): String

    suspend fun updateProgress(bookId: String, progress: ReadProgress)
    suspend fun search(query: String, filter: SearchFilter): SearchResults

    suspend fun onDeckBooks(libraryId: String? = null): List<Book>
    suspend fun recentlyAddedSeries(libraryId: String? = null): List<Series>
    suspend fun recentlyUpdatedSeries(libraryId: String? = null): List<Series>
    suspend fun recentlyAddedBooks(libraryId: String? = null): List<Book>
    suspend fun recentlyReleasedBooks(libraryId: String? = null): List<Book>
    suspend fun recentlyReadBooks(libraryId: String? = null): List<Book>

    /** Returns series count and book count for the given library (null = all libraries). */
    suspend fun libraryStats(libraryId: String?): LibraryStats

    suspend fun availableGenres(): List<String>
    suspend fun availableTags(): List<String>
    suspend fun availablePublishers(): List<String>
}
