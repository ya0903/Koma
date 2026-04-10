package com.koma.client.data.server.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class KavitaSeriesDto(
    val id: Int,
    val name: String,
    val originalName: String = "",
    val localizedName: String = "",
    val sortName: String = "",
    val libraryId: Int,
    val libraryName: String = "",
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val userRating: Float = 0f,
    val format: Int = 0,
    val created: String = "",
    val lastModified: String? = null,
    val metadata: KavitaSeriesMetadataDto = KavitaSeriesMetadataDto(),
)

@Serializable
data class KavitaSeriesMetadataDto(
    val summary: String? = null,
    val genres: List<KavitaGenreDto> = emptyList(),
    val tags: List<KavitaTagDto> = emptyList(),
    val writers: List<KavitaPersonDto> = emptyList(),
    val publishers: List<KavitaPersonDto> = emptyList(),
    val characters: List<KavitaPersonDto> = emptyList(),
    val publicationStatus: Int = 0,
    val language: String? = null,
    val releaseYear: Int = 0,
)

@Serializable
data class KavitaGenreDto(val id: Int = 0, val title: String = "")

@Serializable
data class KavitaTagDto(val id: Int = 0, val title: String = "")

@Serializable
data class KavitaPersonDto(val id: Int = 0, val name: String = "", val role: Int = 0)
