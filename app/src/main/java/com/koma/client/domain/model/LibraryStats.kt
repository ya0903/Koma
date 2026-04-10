package com.koma.client.domain.model

data class LibraryStats(
    val seriesCount: Long,
    val bookCount: Long,
    val sizeBytes: Long = 0,
)
