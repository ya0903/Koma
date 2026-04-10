package com.koma.client.data.server.komga

import com.koma.client.data.server.komga.dto.KomgaBookDto
import com.koma.client.data.server.komga.dto.KomgaLibraryDto
import com.koma.client.data.server.komga.dto.KomgaReadProgressDto
import com.koma.client.data.server.komga.dto.KomgaSeriesDto
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.MediaType
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.model.Series

fun KomgaLibraryDto.toDomain(serverId: String) = Library(
    id = id,
    serverId = serverId,
    name = name,
)

fun KomgaSeriesDto.toDomain(serverId: String, baseUrl: String): Series {
    val base = baseUrl.trimEnd('/')
    return Series(
        id = id,
        serverId = serverId,
        libraryId = libraryId,
        title = metadata.title.ifBlank { name },
        bookCount = booksCount,
        readBookCount = booksReadCount,
        unreadCount = booksUnreadCount,
        thumbUrl = "$base/api/v1/series/$id/thumbnail",
        summary = booksMetadata.summary.ifBlank { metadata.summary }.ifBlank { null },
        genres = metadata.genres,
        tags = metadata.tags,
        status = metadata.status.ifBlank { null },
        publisher = metadata.publisher.ifBlank { null },
        authors = booksMetadata.authors.map { Pair(it.name, it.role) },
        releaseDate = booksMetadata.releaseDate,
        language = metadata.language.ifBlank { null },
        readingDirection = metadata.readingDirection,
    )
}

fun KomgaBookDto.toDomain(serverId: String, baseUrl: String): Book {
    val base = baseUrl.trimEnd('/')
    return Book(
        id = id,
        serverId = serverId,
        seriesId = seriesId,
        libraryId = libraryId,
        seriesTitle = seriesTitle,
        title = metadata.title.ifBlank { name },
        pageCount = media.pagesCount,
        mediaType = media.mediaType.toKomaMediaType(),
        number = number,
        sizeBytes = sizeBytes,
        thumbUrl = "$base/api/v1/books/$id/thumbnail",
        readProgress = readProgress?.toDomain(id),
    )
}

fun KomgaReadProgressDto.toDomain(bookId: String) = ReadProgress(
    bookId = bookId,
    page = page,
    completed = completed,
)

private fun String.toKomaMediaType(): MediaType = when {
    contains("epub") -> MediaType.EPUB
    contains("pdf") -> MediaType.PDF
    else -> MediaType.IMAGE
}
