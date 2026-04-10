package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val page: Int?,
    val locator: String?,
    val note: String?,
    val createdAtEpochMs: Long,
)
