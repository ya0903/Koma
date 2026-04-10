package com.koma.client.domain.model

data class ReadProgress(
    val bookId: String,
    val page: Int,
    val completed: Boolean = false,
    val locator: String? = null,
    val updatedAtEpochMs: Long = 0L,
)
