package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaReadProgressDto(
    val page: Int = 0,
    val completed: Boolean = false,
    val readDate: String? = null,
    val created: String? = null,
    val lastModified: String? = null,
)

@Serializable
data class KomgaReadProgressUpdateDto(
    val page: Int? = null,
    val completed: Boolean? = null,
)
