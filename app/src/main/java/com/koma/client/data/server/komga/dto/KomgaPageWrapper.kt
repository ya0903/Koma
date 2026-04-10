package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaPageWrapper<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0,
    val size: Int = 20,
    val first: Boolean = true,
    val last: Boolean = true,
    val empty: Boolean = false,
    val numberOfElements: Int = 0,
)
