package com.koma.client.data.server.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class KavitaLibraryDto(
    val id: Int,
    val name: String,
    val type: Int = 0,
    val coverImage: String? = null,
    val count: Int = 0,
)
