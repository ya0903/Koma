package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_series")
data class CachedSeriesEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val libraryId: String,
    val title: String,
    val bookCount: Int = 0,
    val unreadCount: Int = 0,
    val thumbUrl: String? = null,
    val summary: String? = null,
)
