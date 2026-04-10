package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaBookDto(
    val id: String,
    val seriesId: String,
    val seriesTitle: String = "",
    val libraryId: String = "",
    val name: String,
    val number: Int = 0,
    val sizeBytes: Long = 0,
    val media: KomgaMediaDto = KomgaMediaDto(),
    val metadata: KomgaBookMetadataDto = KomgaBookMetadataDto(),
    val readProgress: KomgaReadProgressDto? = null,
    val deleted: Boolean = false,
    val oneshot: Boolean = false,
)

@Serializable
data class KomgaBookMetadataDto(
    val title: String = "",
    val number: String = "",
    val numberSort: Float = 0f,
    val summary: String = "",
    val authors: List<KomgaAuthorDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val isbn: String = "",
    val releaseDate: String? = null,
)
