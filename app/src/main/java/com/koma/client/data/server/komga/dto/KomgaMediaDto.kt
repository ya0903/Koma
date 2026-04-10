package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaMediaDto(
    val status: String = "READY",
    val mediaType: String = "",
    val pagesCount: Int = 0,
    val comment: String = "",
    val epubDivinaCompatible: Boolean = false,
)
