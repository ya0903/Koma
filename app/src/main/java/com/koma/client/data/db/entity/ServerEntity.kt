package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.koma.client.domain.server.MediaServerType

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val type: MediaServerType,
    val name: String,
    val baseUrl: String,
    val username: String,
    val encPassword: String,
    val encJwtToken: String?,
    val encSessionCookie: String?,
    val isActive: Boolean,
    val lastSyncAtEpochMs: Long,
)
