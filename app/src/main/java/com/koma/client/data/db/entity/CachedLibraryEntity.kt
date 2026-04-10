package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_libraries")
data class CachedLibraryEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val name: String,
)
