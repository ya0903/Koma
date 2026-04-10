package com.koma.client.domain.server

import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Series

data class SearchResults(
    val series: List<Series>,
    val books: List<Book>,
)
