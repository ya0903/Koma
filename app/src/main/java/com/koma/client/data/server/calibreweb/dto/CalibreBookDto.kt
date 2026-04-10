package com.koma.client.data.server.calibreweb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single book entry from Calibre-Web's `/ajax/listbooks` or `/ajax/book/{id}`.
 *
 * Calibre-Web's JSON uses snake_case keys. Authors are returned as a comma-separated
 * string in the list endpoint, but as a list in the detail endpoint; we handle both
 * by using a custom serializer shim via [authorsRaw] and normalising in the mapper.
 */
@Serializable
data class CalibreBookDto(
    val id: Int,
    val title: String,
    /** Authors as a comma-separated string (list endpoint) */
    val authors: String = "",
    val series: String? = null,
    @SerialName("series_index") val seriesIndex: Float? = null,
    val formats: String = "",          // comma-separated format list, e.g. "EPUB,PDF"
    val comments: String? = null,
    val tags: String = "",             // comma-separated
    val publisher: String = "",
    val pubdate: String = "",
    val languages: String = "",
    val rating: Float = 0f,
    val timestamp: String = "",
    @SerialName("last_modified") val lastModified: String = "",
)

/**
 * Wrapper returned by `/ajax/listbooks` and `/ajax/search`.
 */
@Serializable
data class CalibreListBooksResponse(
    @SerialName("totalBooks") val totalBooks: Int = 0,
    val rows: List<CalibreBookDto> = emptyList(),
)
