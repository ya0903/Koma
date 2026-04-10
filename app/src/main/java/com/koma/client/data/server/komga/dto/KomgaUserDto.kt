package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaUserDto(
    val id: String,
    val email: String,
    val roles: List<String> = emptyList(),
)
