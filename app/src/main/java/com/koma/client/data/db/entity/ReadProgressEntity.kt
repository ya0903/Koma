package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_progress")
data class ReadProgressEntity(
    @PrimaryKey val bookId: String,
    val page: Int,
    val completed: Boolean,
    val locator: String?,
    val updatedAtEpochMs: Long,
    val dirty: Boolean,
)
