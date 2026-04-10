package com.koma.client.domain.model

import com.koma.client.domain.server.MediaServerType

data class Server(
    val id: String,
    val type: MediaServerType,
    val name: String,
    val baseUrl: String,
    val isActive: Boolean,
)
