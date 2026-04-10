package com.koma.client.domain.server

data class SearchFilter(
    val includeSeries: Boolean = true,
    val includeBooks: Boolean = true,
    val readStatus: SeriesFilter.ReadStatus = SeriesFilter.ReadStatus.ALL,
)
