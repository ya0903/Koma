package com.koma.client.data.server.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class KavitaLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class KavitaLoginResponse(
    val token: String,
    val apiKey: String = "",
)
