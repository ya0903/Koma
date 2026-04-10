package com.koma.client.domain.model

enum class MediaType { IMAGE, EPUB, PDF }

data class Book(
    val id: String,
    val serverId: String,
    val seriesId: String,
    val libraryId: String = "",
    val seriesTitle: String = "",
    val title: String,
    val pageCount: Int,
    val mediaType: MediaType,
    val number: Int? = null,
    val sizeBytes: Long? = null,
    val thumbUrl: String? = null,
    val readProgress: ReadProgress? = null,
)
