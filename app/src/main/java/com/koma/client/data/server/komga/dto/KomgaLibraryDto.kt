package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaLibraryDto(
    val id: String,
    val name: String,
    val unavailable: Boolean = false,
)
