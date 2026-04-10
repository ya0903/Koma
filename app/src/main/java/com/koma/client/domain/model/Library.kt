package com.koma.client.domain.model

data class Library(
    val id: String,
    val serverId: String,
    val name: String,
    val unreadCount: Int = 0,
)
