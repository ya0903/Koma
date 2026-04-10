package com.koma.client.data.server.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class KavitaSearchResultDto(
    val series: List<KavitaSearchSeriesResultDto> = emptyList(),
    val collections: List<KavitaSearchCollectionDto> = emptyList(),
    val readingLists: List<KavitaSearchReadingListDto> = emptyList(),
    val persons: List<KavitaPersonDto> = emptyList(),
    val genres: List<KavitaGenreDto> = emptyList(),
    val tags: List<KavitaTagDto> = emptyList(),
    val files: List<KavitaSearchFileDto> = emptyList(),
    val chapters: List<KavitaChapterDto> = emptyList(),
    val libraries: List<KavitaLibraryDto> = emptyList(),
)

@Serializable
data class KavitaSearchSeriesResultDto(
    val seriesId: Int,
    val name: String = "",
    val localizedName: String = "",
    val originalName: String = "",
    val sortName: String = "",
    val libraryId: Int = 0,
    val libraryName: String = "",
    val format: Int = 0,
)

@Serializable
data class KavitaSearchCollectionDto(
    val id: Int,
    val title: String = "",
)

@Serializable
data class KavitaSearchReadingListDto(
    val id: Int,
    val title: String = "",
)

@Serializable
data class KavitaSearchFileDto(
    val seriesId: Int = 0,
    val name: String = "",
    val format: Int = 0,
)
