package com.koma.client.data.server.opds

import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.MediaType
import com.koma.client.domain.model.Series

// ─── Constants ────────────────────────────────────────────────────────────────

/** Virtual library ID used for the flat OPDS root library. */
internal const val OPDS_LIBRARY_ID = "opds_root"
internal const val OPDS_LIBRARY_NAME = "OPDS Library"

/** Series ID for books with no series metadata. */
internal const val OPDS_UNCATEGORIZED_ID = "opds_uncategorized"
internal const val OPDS_UNCATEGORIZED_NAME = "Uncategorized"

// ─── Library mapping ──────────────────────────────────────────────────────────

/**
 * Maps an OPDS navigation [OpdsEntry] to a [Library].
 * Each navigation entry represents a sub-catalog (library / category).
 */
fun OpdsEntry.toLibrary(serverId: String): Library = Library(
    id = opdsLibraryId(id),
    serverId = serverId,
    name = title,
)

fun opdsRootLibrary(serverId: String): Library = Library(
    id = OPDS_LIBRARY_ID,
    serverId = serverId,
    name = OPDS_LIBRARY_NAME,
)

/** Stable library ID from an OPDS entry ID. */
internal fun opdsLibraryId(entryId: String): String =
    "opds_lib_${entryId.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(64)}"

// ─── Series grouping ──────────────────────────────────────────────────────────

/**
 * Groups a flat list of OPDS acquisition entries into virtual [Series].
 *
 * Books are grouped by the `author` field (as a proxy for series when real
 * series metadata is absent) or, if the title contains a recognisable "Book N"
 * pattern, by the derived series title. Falls back to each book as its own
 * single-book "series".
 *
 * The grouping strategy:
 * 1. Use `series` extracted from `categories` if present (term prefixed "series:").
 * 2. Otherwise group all books under [OPDS_UNCATEGORIZED_NAME].
 */
fun groupOpdsEntriesIntoSeries(
    entries: List<OpdsEntry>,
    serverId: String,
    libraryId: String,
): List<Series> {
    // Try to find series name from categories (some OPDS servers tag series: prefix)
    val grouped = entries.groupBy { entry ->
        entry.categories.firstOrNull { it.startsWith("series:", ignoreCase = true) }
            ?.removePrefix("series:")?.trim()
            ?: entry.categories.firstOrNull()  // use first category if any
    }

    val namedSeries = grouped
        .filterKeys { it != null }
        .map { (seriesName, seriesEntries) ->
            val sid = opdsSeriesId(seriesName!!)
            val first = seriesEntries.first()
            Series(
                id = sid,
                serverId = serverId,
                libraryId = libraryId,
                title = seriesName,
                bookCount = seriesEntries.size,
                readBookCount = 0,
                unreadCount = seriesEntries.size,
                thumbUrl = first.thumbnailUrl,
                summary = null,
            )
        }
        .sortedBy { it.title }

    val uncategorized = grouped[null]
    val uncategorizedSeries: List<Series> = if (!uncategorized.isNullOrEmpty()) {
        val first = uncategorized.first()
        listOf(
            Series(
                id = OPDS_UNCATEGORIZED_ID,
                serverId = serverId,
                libraryId = libraryId,
                title = OPDS_UNCATEGORIZED_NAME,
                bookCount = uncategorized.size,
                readBookCount = 0,
                unreadCount = uncategorized.size,
                thumbUrl = first.thumbnailUrl,
                summary = null,
            )
        )
    } else emptyList()

    return namedSeries + uncategorizedSeries
}

/** Stable virtual series ID from a series name. */
internal fun opdsSeriesId(seriesName: String): String =
    "opds_series_${seriesName.lowercase().replace(' ', '_').replace(Regex("[^a-z0-9_]"), "").take(64)}"

/**
 * Returns the [OpdsEntry] items that belong to the virtual series identified by [seriesId].
 */
fun filterEntriesForSeries(
    entries: List<OpdsEntry>,
    seriesId: String,
): List<OpdsEntry> = when (seriesId) {
    OPDS_UNCATEGORIZED_ID -> entries.filter { entry ->
        entry.categories.none { it.startsWith("series:", ignoreCase = true) || it.isNotBlank() }
    }
    else -> entries.filter { entry ->
        val catSeriesName = entry.categories
            .firstOrNull { it.startsWith("series:", ignoreCase = true) }
            ?.removePrefix("series:")?.trim()
            ?: entry.categories.firstOrNull()
        catSeriesName != null && opdsSeriesId(catSeriesName) == seriesId
    }
}

// ─── Book mapping ─────────────────────────────────────────────────────────────

/**
 * Maps an OPDS acquisition [OpdsEntry] to the unified [Book] domain model.
 */
fun OpdsEntry.toBook(
    serverId: String,
    libraryId: String,
    seriesId: String,
): Book {
    val bestLink = pickBestAcquisitionLink(acquisitionLinks)
    val mediaType = detectOpdsMediaType(bestLink?.type ?: "")

    return Book(
        id = id.ifBlank { title },
        serverId = serverId,
        seriesId = seriesId,
        libraryId = libraryId,
        seriesTitle = "",
        title = title,
        pageCount = 0,       // OPDS has no page-count concept
        mediaType = mediaType,
        number = null,
        sizeBytes = null,
        thumbUrl = thumbnailUrl,
        readProgress = null, // OPDS has no progress sync
    )
}

// ─── Helper functions ─────────────────────────────────────────────────────────

/**
 * Picks the best download link from a list of acquisition links.
 * Priority: EPUB > PDF > CBZ/CBR > first available.
 */
internal fun pickBestAcquisitionLink(links: List<OpdsLink>): OpdsLink? {
    val priority = listOf(
        "application/epub+zip",
        "application/pdf",
        "application/x-cbz",
        "application/x-cbr",
    )
    for (mimeType in priority) {
        val link = links.firstOrNull { it.type.startsWith(mimeType, ignoreCase = true) }
        if (link != null) return link
    }
    return links.firstOrNull()
}

/** Maps an OPDS MIME type to a [MediaType]. */
internal fun detectOpdsMediaType(mimeType: String): MediaType = when {
    mimeType.contains("epub", ignoreCase = true) -> MediaType.EPUB
    mimeType.contains("pdf", ignoreCase = true) -> MediaType.PDF
    else -> MediaType.IMAGE
}
