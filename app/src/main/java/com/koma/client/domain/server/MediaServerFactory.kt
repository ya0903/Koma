package com.koma.client.domain.server

interface MediaServerFactory {
    fun create(
        type: MediaServerType,
        id: String,
        baseUrl: String,
        username: String,
        password: String,
    ): MediaServer
}
