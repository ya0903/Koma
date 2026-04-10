package com.koma.client.data.server.opds

import com.koma.client.data.auth.CredentialStore
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
import okhttp3.Credentials
import okhttp3.OkHttpClient

/**
 * [MediaServer] implementation for generic OPDS servers.
 *
 * OPDS (Open Publication Distribution System) is an Atom-XML catalog format
 * supported by many self-hosted servers: Ubooquity, COPS, Mango, Calibre-OPDS,
 * Komga (secondary), etc.
 *
 * Structural decisions:
 * - Libraries: navigation entries in the root feed (each sub-catalog = a library).
 *   If the root has no navigation entries, a single synthetic "OPDS Library" is used.
 * - Series:    virtual — derived from `category` metadata on acquisition entries.
 *              Books without any category go into the "Uncategorized" virtual series.
 * - Books:     acquisition entries in the sub-feed for a library.
 * - Progress:  local-only; OPDS has no progress-sync API.
 * - Search:    uses the OpenSearch URL from the root feed when available;
 *              falls back to client-side filtering.
 *
 * Auth: HTTP Basic (same pattern as Calibre-Web / Komga).
 */
class OpdsMediaServer(
    override val id: String,
    private val baseUrl: String,
    private val credentialStore: CredentialStore,
    private val baseOkHttpClient: OkHttpClient,
) : MediaServer {

    override val type: MediaServerType = MediaServerType.OPDS
    override val capabilities: ServerCapabilities = ServerCapabilities.opdsDefaults()

    // ─── HTTP setup ───────────────────────────────────────────────────────────

    private val okHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val username = credentialStore.getUsername(id) ?: ""
                val password = credentialStore.getPassword(id) ?: ""
                val request = chain.request().newBuilder()
                    .apply {
                        if (username.isNotBlank()) {
                            header("Authorization", Credentials.basic(username, password))
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val api: OpdsApi by lazy {
        OpdsApi(baseUrl = baseUrl, client = okHttpClient)
    }

    /** Exposes the authenticated OkHttpClient for use by Coil image loading. */
    fun authenticatedClient(): OkHttpClient = okHttpClient

    // ─── Root feed cache (in-memory, per-instance) ────────────────────────────

    @Volatile private var cachedRootFeed: OpdsFeed? = null

    private suspend fun rootFeed(): OpdsFeed {
        return cachedRootFeed ?: api.getRootFeed().also { cachedRootFeed = it }
    }

    // ─── Library → navigation-entry URL lookup ────────────────────────────────

    /**
     * Maps virtual library ID → navigation URL for the sub-feed.
     * Built from the root feed's navigation entries.
     */
    @Volatile private var libraryUrlMap: Map<String, String> = emptyMap()

    private suspend fun ensureLibraryMap() {
        if (libraryUrlMap.isNotEmpty()) return
        val feed = rootFeed()
        libraryUrlMap = feed.entries
            .filter { it.isNavigation }
            .associate { entry -> opdsLibraryId(entry.id) to (entry.navigationLink ?: "") }
    }

    // ─── Core API ─────────────────────────────────────────────────────────────

    override suspend fun authenticate(): Result<Unit> = runCatching {
        cachedRootFeed = null // invalidate cache on re-auth
        api.getRootFeed()
        Unit
    }

    override fun libraries(): Flow<List<Library>> = flow {
        val feed = rootFeed()
        val navEntries = feed.entries.filter { it.isNavigation }
        val libraries = if (navEntries.isNotEmpty()) {
            navEntries.map { it.toLibrary(id) }
        } else {
            // Root feed has only acquisition entries — expose as a single flat library
            listOf(opdsRootLibrary(id))
        }
        emit(libraries)
    }

    override fun series(libraryId: String, filter: SeriesFilter): Flow<List<Series>> = flow {
        val entries = loadAcquisitionEntries(libraryId)
        emit(groupOpdsEntriesIntoSeries(entries, id, libraryId))
    }

    override suspend fun seriesDetail(seriesId: String): Series {
        // We need to reconstruct a Series from cached data.
        // Walk all known libraries and find the one containing this series.
        val feed = rootFeed()
        val navEntries = feed.entries.filter { it.isNavigation }
        val libraryIds = if (navEntries.isNotEmpty()) {
            navEntries.map { opdsLibraryId(it.id) }
        } else {
            listOf(OPDS_LIBRARY_ID)
        }
        for (libId in libraryIds) {
            val entries = loadAcquisitionEntries(libId)
            val series = groupOpdsEntriesIntoSeries(entries, id, libId)
            val match = series.firstOrNull { it.id == seriesId }
            if (match != null) return match
        }
        return Series(
            id = seriesId,
            serverId = id,
            libraryId = OPDS_LIBRARY_ID,
            title = OPDS_UNCATEGORIZED_NAME,
        )
    }

    override fun books(seriesId: String): Flow<List<Book>> = flow {
        // Find the library that contains this series
        val feed = rootFeed()
        val navEntries = feed.entries.filter { it.isNavigation }
        val libraryIds = if (navEntries.isNotEmpty()) {
            navEntries.map { opdsLibraryId(it.id) }
        } else {
            listOf(OPDS_LIBRARY_ID)
        }
        for (libId in libraryIds) {
            val entries = loadAcquisitionEntries(libId)
            val seriesInLib = groupOpdsEntriesIntoSeries(entries, id, libId)
            if (seriesInLib.any { it.id == seriesId }) {
                val filtered = filterEntriesForSeries(entries, seriesId)
                emit(filtered.map { it.toBook(id, libId, seriesId) })
                return@flow
            }
        }
        emit(emptyList())
    }

    override suspend fun book(bookId: String): Book {
        // There is no single-book endpoint in OPDS; scan all entries
        val feed = rootFeed()
        val navEntries = feed.entries.filter { it.isNavigation }
        val libraryIds = if (navEntries.isNotEmpty()) {
            navEntries.map { opdsLibraryId(it.id) }
        } else {
            listOf(OPDS_LIBRARY_ID)
        }
        for (libId in libraryIds) {
            val entries = loadAcquisitionEntries(libId)
            val entry = entries.firstOrNull { it.id == bookId || it.title == bookId }
            if (entry != null) {
                return entry.toBook(id, libId, OPDS_UNCATEGORIZED_ID)
            }
        }
        throw NoSuchElementException("Book not found in OPDS feed: $bookId")
    }

    // ─── URL helpers ──────────────────────────────────────────────────────────

    /**
     * OPDS books are full files (EPUB/PDF) — there is no page-image concept.
     */
    override suspend fun pageUrl(bookId: String, page: Int): String = ""

    /**
     * Returns the download URL for the best available format of the book.
     */
    override suspend fun fileUrl(bookId: String): String {
        val feed = rootFeed()
        val navEntries = feed.entries.filter { it.isNavigation }
        val libraryIds = if (navEntries.isNotEmpty()) {
            navEntries.map { opdsLibraryId(it.id) }
        } else {
            listOf(OPDS_LIBRARY_ID)
        }
        for (libId in libraryIds) {
            val entries = loadAcquisitionEntries(libId)
            val entry = entries.firstOrNull { it.id == bookId || it.title == bookId }
            if (entry != null) {
                return pickBestAcquisitionLink(entry.acquisitionLinks)?.href ?: ""
            }
        }
        return ""
    }

    override suspend fun thumbnailUrl(id: String, kind: ThumbKind): String {
        // id here is the book's domain ID (= OpdsEntry.id)
        val feed = rootFeed()
        val navEntries = feed.entries.filter { it.isNavigation }
        val libraryIds = if (navEntries.isNotEmpty()) {
            navEntries.map { opdsLibraryId(it.id) }
        } else {
            listOf(OPDS_LIBRARY_ID)
        }
        for (libId in libraryIds) {
            val entries = loadAcquisitionEntries(libId)
            val entry = entries.firstOrNull { it.id == id || it.title == id }
            if (entry != null) return entry.thumbnailUrl ?: ""
        }
        return ""
    }

    // ─── Progress ─────────────────────────────────────────────────────────────

    /** No-op: OPDS has no progress sync API. */
    override suspend fun updateProgress(bookId: String, progress: ReadProgress) {
        // intentional no-op
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    override suspend fun search(query: String, filter: SearchFilter): SearchResults {
        val root = rootFeed()
        val lowerQuery = query.lowercase()

        if (root.searchUrl != null) {
            // Use the server's OpenSearch endpoint if available.
            // Replace the OpenSearch template parameter {searchTerms} with the query.
            val searchUrl = root.searchUrl
                .replace("{searchTerms}", query)
                .replace("%7BsearchTerms%7D", query)
            return try {
                val searchFeed = api.getFeed(searchUrl)
                val acquisitions = searchFeed.entries.filter { it.isAcquisition }
                val series = groupOpdsEntriesIntoSeries(acquisitions, id, OPDS_LIBRARY_ID)
                val books = acquisitions.map { it.toBook(id, OPDS_LIBRARY_ID, OPDS_UNCATEGORIZED_ID) }
                SearchResults(series = series, books = books)
            } catch (e: Exception) {
                // Fall back to client-side search if server search fails
                clientSideSearch(lowerQuery, filter)
            }
        }

        return clientSideSearch(lowerQuery, filter)
    }

    private suspend fun clientSideSearch(lowerQuery: String, filter: SearchFilter): SearchResults {
        val feed = rootFeed()
        val navEntries = feed.entries.filter { it.isNavigation }
        val libraryIds = if (navEntries.isNotEmpty()) {
            navEntries.map { opdsLibraryId(it.id) }
        } else {
            listOf(OPDS_LIBRARY_ID)
        }

        val allEntries = mutableListOf<OpdsEntry>()
        for (libId in libraryIds) {
            allEntries.addAll(loadAcquisitionEntries(libId))
        }

        val matchingEntries = allEntries.filter { entry ->
            entry.title.contains(lowerQuery, ignoreCase = true) ||
                entry.author?.contains(lowerQuery, ignoreCase = true) == true ||
                entry.summary?.contains(lowerQuery, ignoreCase = true) == true
        }

        val series = groupOpdsEntriesIntoSeries(matchingEntries, id, OPDS_LIBRARY_ID)
        val books = matchingEntries.map { it.toBook(id, OPDS_LIBRARY_ID, OPDS_UNCATEGORIZED_ID) }
        return SearchResults(series = series, books = books)
    }

    // ─── Dashboard ────────────────────────────────────────────────────────────

    override suspend fun onDeckBooks(libraryId: String?): List<Book> = emptyList()

    override suspend fun recentlyAddedSeries(libraryId: String?): List<Series> {
        val entries = recentAcquisitionEntries(libraryId)
        return groupOpdsEntriesIntoSeries(entries, id, libraryId ?: OPDS_LIBRARY_ID)
    }

    override suspend fun recentlyUpdatedSeries(libraryId: String?): List<Series> =
        recentlyAddedSeries(libraryId)

    override suspend fun recentlyAddedBooks(libraryId: String?): List<Book> {
        val entries = recentAcquisitionEntries(libraryId).take(20)
        return entries.map { it.toBook(id, libraryId ?: OPDS_LIBRARY_ID, OPDS_UNCATEGORIZED_ID) }
    }

    override suspend fun recentlyReleasedBooks(libraryId: String?): List<Book> =
        recentlyAddedBooks(libraryId)

    override suspend fun recentlyReadBooks(libraryId: String?): List<Book> = emptyList()

    // ─── Stats + metadata ─────────────────────────────────────────────────────

    override suspend fun libraryStats(libraryId: String?): LibraryStats {
        val entries = loadAcquisitionEntries(libraryId ?: OPDS_LIBRARY_ID)
        val series = groupOpdsEntriesIntoSeries(entries, id, libraryId ?: OPDS_LIBRARY_ID)
        return LibraryStats(
            seriesCount = series.size.toLong(),
            bookCount = entries.size.toLong(),
        )
    }

    override suspend fun availableGenres(): List<String> = emptyList()
    override suspend fun availableTags(): List<String> = emptyList()
    override suspend fun availablePublishers(): List<String> = emptyList()

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Loads all acquisition entries for a given [libraryId].
     *
     * If [libraryId] is [OPDS_LIBRARY_ID] (the virtual root library), loads from the
     * root feed directly. Otherwise resolves the navigation URL for the library and
     * fetches that sub-feed, following "next" pagination links up to a reasonable limit.
     */
    private suspend fun loadAcquisitionEntries(libraryId: String): List<OpdsEntry> {
        val startUrl: String = when (libraryId) {
            OPDS_LIBRARY_ID -> {
                val feed = rootFeed()
                val rootAcquisitions = feed.entries.filter { it.isAcquisition }
                if (rootAcquisitions.isNotEmpty()) {
                    // Root has direct acquisition entries — no sub-feed needed
                    return paginateAcquisitions(null, rootAcquisitions, feed.nextUrl)
                }
                // No acquisition entries at root — return empty
                return emptyList()
            }
            else -> {
                ensureLibraryMap()
                libraryUrlMap[libraryId] ?: return emptyList()
            }
        }

        val firstFeed = api.getFeed(startUrl)
        val initialEntries = firstFeed.entries.filter { it.isAcquisition }
        return paginateAcquisitions(startUrl, initialEntries, firstFeed.nextUrl)
    }

    /**
     * Follows "next" pagination links and accumulates acquisition entries.
     * Stops after [MAX_PAGES] pages to prevent runaway fetches.
     */
    private suspend fun paginateAcquisitions(
        @Suppress("UNUSED_PARAMETER") startUrl: String?,
        initial: List<OpdsEntry>,
        firstNextUrl: String?,
    ): List<OpdsEntry> {
        val all = initial.toMutableList()
        var nextUrl: String? = firstNextUrl
        var page = 0
        while (nextUrl != null && page < MAX_PAGES) {
            val feed = api.getFeed(nextUrl)
            all.addAll(feed.entries.filter { it.isAcquisition })
            nextUrl = feed.nextUrl
            page++
        }
        return all
    }

    private suspend fun recentAcquisitionEntries(libraryId: String?): List<OpdsEntry> {
        if (libraryId != null) return loadAcquisitionEntries(libraryId)
        // No library specified — try root feed entries
        val feed = rootFeed()
        return feed.entries.filter { it.isAcquisition }
    }

    companion object {
        private const val MAX_PAGES = 10
    }
}
