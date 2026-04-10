package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val bookId: String,
    val serverId: String,
    val bookTitle: String,
    val seriesId: String = "",
    val seriesTitle: String = "",
    val thumbUrl: String = "",
    val state: String,        // QUEUED, DOWNLOADING, COMPLETE, FAILED
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val filePath: String? = null,
    val fileUrl: String = "",
    val mediaType: String = "IMAGE",  // IMAGE, EPUB, PDF
    val error: String? = null,
    val updatedAtEpochMs: Long = 0,
)
