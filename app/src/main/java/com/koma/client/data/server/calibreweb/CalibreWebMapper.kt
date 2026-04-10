package com.koma.client.data.server.calibreweb

import com.koma.client.data.server.calibreweb.dto.CalibreBookDto
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.MediaType
import com.koma.client.domain.model.Series

// ─── Constants ────────────────────────────────────────────────────────────────

/** Virtual library ID used for Calibre-Web's single flat library. */
internal const val CALIBRE_LIBRARY_ID = "calibre_library"
internal const val CALIBRE_LIBRARY_NAME = "Calibre Library"

/** Series ID used for books that have no series metadata. */
internal const val CALIBRE_UNCATEGORIZED_ID = "calibre_uncategorized"
internal const val CALIBRE_UNCATEGORIZED_NAME = "Uncategorized"

// ─── Library mapping ──────────────────────────────────────────────────────────

/**
 * Calibre-Web has a single flat library. We synthesise a Library entry for it.
 */
fun calibreLibrary(serverId: String): Library = Library(
    id = CALIBRE_LIBRARY_ID,
    serverId = serverId,
    name = CALIBRE_LIBRARY_NAME,
)

// ─── Series grouping ──────────────────────────────────────────────────────────

/**
 * Groups a flat list of Calibre books into virtual [Series] entries based on
 * the `series` metadata field. Books without a series are grouped under
 * [CALIBRE_UNCATEGORIZED_NAME].
 *
 * The virtual series ID is the URL-encoded series name prefixed with
 * `"calibre_series_"` so that downstream code can identify it.
 */
fun groupBooksIntoSeries(
    books: List<CalibreBookDto>,
    serverId: String,
    baseUrl: String,
): List<Series> {
    val base = baseUrl.trimEnd('/')
    val grouped = books.groupBy { it.series?.ifBlank { null } }

    val namedSeries: List<Series> = grouped
        .filterKeys { it != null }
        .map { (seriesName, seriesBooks) ->
            val id = calibreSeriesId(seriesName!!)
            // Use the cover of the first book (sorted by series_index) as the series thumb
            val firstBook = seriesBooks.minByOrNull { it.seriesIndex ?: 0f }
            Series(
                id = id,
                serverId = serverId,
                libraryId = CALIBRE_LIBRARY_ID,
                title = seriesName,
                bookCount = seriesBooks.size,
                readBookCount = 0,
                unreadCount = seriesBooks.size,
                thumbUrl = firstBook?.let { "$base/cover/${it.id}" },
                summary = null,
            )
        }
        .sortedBy { it.title }

    // Uncategorised bucket — only include if there are books without a series
    val uncategorized = grouped[null]
    val uncategorizedSeries: List<Series> = if (!uncategorized.isNullOrEmpty()) {
        val firstBook = uncategorized.first()
        listOf(
            Series(
                id = CALIBRE_UNCATEGORIZED_ID,
                serverId = serverId,
                libraryId = CALIBRE_LIBRARY_ID,
                title = CALIBRE_UNCATEGORIZED_NAME,
                bookCount = uncategorized.size,
                readBookCount = 0,
                unreadCount = uncategorized.size,
                thumbUrl = "$base/cover/${firstBook.id}",
                summary = null,
            )
        )
    } else emptyList()

    return namedSeries + uncategorizedSeries
}

/**
 * Returns the books that belong to the virtual series identified by [seriesId].
 *
 * - For [CALIBRE_UNCATEGORIZED_ID]: returns books with no series metadata.
 * - For named series IDs: matches books by re-computing their series ID and comparing.
 */
fun filterBooksForSeries(
    books: List<CalibreBookDto>,
    seriesId: String,
): List<CalibreBookDto> = when (seriesId) {
    CALIBRE_UNCATEGORIZED_ID -> books.filter { it.series.isNullOrBlank() }
    else -> books.filter { book ->
        val bookSeriesName = book.series?.ifBlank { null }
        bookSeriesName != null && calibreSeriesId(bookSeriesName) == seriesId
    }
}

// ─── Book mapping ─────────────────────────────────────────────────────────────

/**
 * Maps a [CalibreBookDto] to the unified [Book] domain model.
 *
 * - IDs are integers in Calibre-Web; we stringify them.
 * - Cover URL: `{baseUrl}/cover/{id}`
 * - Download URL: `{baseUrl}/opds/download/{id}/{format}/`
 *   Best format priority: EPUB > PDF > CBZ > first available
 * - MediaType is detected from the available formats list.
 */
fun CalibreBookDto.toDomain(
    serverId: String,
    baseUrl: String,
    seriesId: String = deriveSeriesId(this),
): Book {
    val base = baseUrl.trimEnd('/')
    val formatList = parseFormats(formats)
    val bestFormat = pickBestFormat(formatList)
    val mediaType = detectMediaType(formatList)
    val authorList = parseAuthors(authors)

    return Book(
        id = id.toString(),
        serverId = serverId,
        seriesId = seriesId,
        libraryId = CALIBRE_LIBRARY_ID,
        seriesTitle = series ?: CALIBRE_UNCATEGORIZED_NAME,
        title = title,
        pageCount = 0,          // Calibre-Web does not expose page counts via JSON API
        mediaType = mediaType,
        number = seriesIndex?.toInt(),
        sizeBytes = null,
        thumbUrl = "$base/cover/$id",
        readProgress = null,    // local-only; Calibre-Web has no progress sync API
    )
}

// ─── Helper functions ─────────────────────────────────────────────────────────

/** Stable virtual series ID from a series name. */
internal fun calibreSeriesId(seriesName: String): String =
    "calibre_series_${seriesName.lowercase().replace(' ', '_').replace(Regex("[^a-z0-9_]"), "")}"

private fun deriveSeriesId(book: CalibreBookDto): String =
    if (book.series.isNullOrBlank()) CALIBRE_UNCATEGORIZED_ID else calibreSeriesId(book.series)

/** Parses a comma-separated formats string like `"EPUB,PDF"` into an uppercase list. */
internal fun parseFormats(raw: String): List<String> =
    raw.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }

/**
 * Parses an authors string into individual author names.
 *
 * Calibre-Web's list endpoint uses `|` as the separator between authors (e.g.
 * `"Frank Herbert|Isaac Asimov"`). If no `|` is present we fall back to splitting
 * on commas.
 */
internal fun parseAuthors(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return if (raw.contains('|')) {
        raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Chooses the best available format: EPUB > PDF > CBZ > first entry. */
internal fun pickBestFormat(formats: List<String>): String? {
    val priority = listOf("EPUB", "PDF", "CBZ")
    for (fmt in priority) {
        if (formats.contains(fmt)) return fmt
    }
    return formats.firstOrNull()
}

/** Maps available formats to a [MediaType]. */
internal fun detectMediaType(formats: List<String>): MediaType = when {
    formats.contains("EPUB") -> MediaType.EPUB
    formats.contains("PDF") -> MediaType.PDF
    else -> MediaType.IMAGE  // CBZ / CBR / etc.
}

/**
 * Builds the download URL for a given book ID and format.
 * Calibre-Web OPDS download endpoint: `GET /opds/download/{book_id}/{format}/`
 */
internal fun calibreDownloadUrl(baseUrl: String, bookId: Int, format: String): String =
    "${baseUrl.trimEnd('/')}/opds/download/$bookId/${format.lowercase()}/"
