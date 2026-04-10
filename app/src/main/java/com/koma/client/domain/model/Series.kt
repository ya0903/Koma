package com.koma.client.domain.model

data class Series(
    val id: String,
    val serverId: String,
    val libraryId: String,
    val title: String,
    val bookCount: Int = 0,
    val readBookCount: Int = 0,
    val unreadCount: Int = 0,
    val thumbUrl: String? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: String? = null,
    val publisher: String? = null,
    val authors: List<Pair<String, String>> = emptyList(),
    val releaseDate: String? = null,
    val language: String? = null,
    val readingDirection: String? = null,
)
