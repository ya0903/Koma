package com.koma.client.data.server.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class KavitaProgressDto(
    val volumeId: Int,
    val chapterId: Int,
    val pageNum: Int,
    val seriesId: Int,
    val libraryId: Int,
    val bookScrollId: String? = null,
)
