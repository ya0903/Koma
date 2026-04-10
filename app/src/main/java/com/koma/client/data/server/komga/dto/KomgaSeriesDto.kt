package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaSeriesDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val booksCount: Int,
    val booksReadCount: Int = 0,
    val booksUnreadCount: Int = 0,
    val booksInProgressCount: Int = 0,
    val metadata: KomgaSeriesMetadataDto = KomgaSeriesMetadataDto(),
    val booksMetadata: KomgaBooksMetadataAggregationDto = KomgaBooksMetadataAggregationDto(),
    val deleted: Boolean = false,
    val oneshot: Boolean = false,
)

@Serializable
data class KomgaSeriesMetadataDto(
    val title: String = "",
    val titleSort: String = "",
    val summary: String = "",
    val status: String = "ONGOING",
    val readingDirection: String? = null,
    val publisher: String = "",
    val language: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class KomgaBooksMetadataAggregationDto(
    val summary: String = "",
    val authors: List<KomgaAuthorDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val releaseDate: String? = null,
)

@Serializable
data class KomgaAuthorDto(
    val name: String,
    val role: String,
)
