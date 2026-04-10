package com.koma.client.domain.model

data class Bookmark(
    val id: String,
    val bookId: String,
    val page: Int?,
    val locator: String?,
    val note: String?,
    val createdAtEpochMs: Long,
)
