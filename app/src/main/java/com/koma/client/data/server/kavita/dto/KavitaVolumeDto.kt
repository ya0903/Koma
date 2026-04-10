package com.koma.client.data.server.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class KavitaVolumeDto(
    val id: Int,
    val number: Int = 0,
    val name: String = "",
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val seriesId: Int = 0,
    val chapters: List<KavitaChapterDto> = emptyList(),
)

@Serializable
data class KavitaChapterDto(
    val id: Int,
    val number: String = "0",
    val range: String = "",
    val title: String = "",
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isSpecial: Boolean = false,
    val volumeId: Int = 0,
    val seriesId: Int = 0,
    val created: String = "",
    val releaseDate: String? = null,
    val summary: String? = null,
    val wordCount: Long = 0,
)
