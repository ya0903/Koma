package com.koma.client.data.server.kavita

import com.koma.client.data.server.kavita.dto.KavitaChapterDto
import com.koma.client.data.server.kavita.dto.KavitaLibraryDto
import com.koma.client.data.server.kavita.dto.KavitaSeriesDto
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.MediaType
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.model.Series

fun KavitaLibraryDto.toDomain(serverId: String): Library = Library(
    id = id.toString(),
    serverId = serverId,
    name = name,
)

fun KavitaSeriesDto.toDomain(serverId: String, baseUrl: String): Series {
    val base = baseUrl.trimEnd('/')
    val totalChapters = 0  // Kavita volumes/chapters loaded separately
    val readCount = if (pages > 0) (pagesRead * totalChapters / pages) else 0
    return Series(
        id = id.toString(),
        serverId = serverId,
        libraryId = libraryId.toString(),
        title = localizedName.ifBlank { name },
        bookCount = 0,      // populated when volumes are fetched
        readBookCount = 0,
        unreadCount = 0,
        thumbUrl = "$base/api/Image/series-cover?seriesId=$id",
        summary = metadata.summary?.ifBlank { null },
        genres = metadata.genres.map { it.title },
        tags = metadata.tags.map { it.title },
        status = kavitaPublicationStatus(metadata.publicationStatus),
        publisher = metadata.publishers.firstOrNull()?.name?.ifBlank { null },
        authors = metadata.writers.map { Pair(it.name, "Writer") },
        releaseDate = if (metadata.releaseYear > 0) metadata.releaseYear.toString() else null,
        language = metadata.language?.ifBlank { null },
        readingDirection = null,
    )
}

fun KavitaChapterDto.toDomain(
    serverId: String,
    baseUrl: String,
    seriesId: Int,
    seriesTitle: String = "",
): Book {
    val base = baseUrl.trimEnd('/')
    val chapterNum = number.toIntOrNull()
    val titleDisplay = when {
        title.isNotBlank() -> title
        isSpecial -> range.ifBlank { "Special" }
        number.isNotBlank() && number != "0" -> "Chapter $number"
        else -> range.ifBlank { "Chapter" }
    }
    val mediaType = when {
        // Kavita uses format codes; word count > 0 implies EPUB/text format
        wordCount > 0 -> MediaType.EPUB
        else -> MediaType.IMAGE
    }
    val progress = if (pagesRead > 0) {
        ReadProgress(
            bookId = id.toString(),
            page = pagesRead,
            completed = pagesRead >= pages,
        )
    } else null

    return Book(
        id = id.toString(),
        serverId = serverId,
        seriesId = seriesId.toString(),
        seriesTitle = seriesTitle,
        title = titleDisplay,
        pageCount = pages,
        mediaType = mediaType,
        number = chapterNum,
        sizeBytes = null,
        thumbUrl = "$base/api/Image/chapter-cover?chapterId=$id",
        readProgress = progress,
    )
}

private fun kavitaPublicationStatus(code: Int): String? = when (code) {
    0 -> "Ongoing"
    1 -> "Hiatus"
    2 -> "Completed"
    3 -> "Cancelled"
    4 -> "Ended"
    else -> null
}
