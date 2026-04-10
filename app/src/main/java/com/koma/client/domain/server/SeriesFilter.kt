package com.koma.client.domain.server

data class SeriesFilter(
    val readStatus: ReadStatus = ReadStatus.ALL,
    val sortBy: SeriesSort = SeriesSort.TITLE,
) {
    enum class ReadStatus { ALL, UNREAD, IN_PROGRESS, COMPLETED }
}

enum class SeriesSort { TITLE, DATE_ADDED, LAST_READ }
