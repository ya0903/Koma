package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaPageDto(
    val number: Int,
    val fileName: String = "",
    val mediaType: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
)
