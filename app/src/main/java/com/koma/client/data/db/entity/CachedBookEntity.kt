package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_books")
data class CachedBookEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val seriesId: String,
    val title: String,
    val number: Int? = null,
    val pageCount: Int = 0,
    val mediaType: String = "IMAGE",
    val sizeBytes: Long? = null,
    val thumbUrl: String? = null,
)
