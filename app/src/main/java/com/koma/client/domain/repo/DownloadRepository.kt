package com.koma.client.domain.repo

import com.koma.client.domain.model.Download
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeAll(): Flow<List<Download>>
    suspend fun enqueue(
        bookId: String,
        serverId: String,
        title: String,
        fileUrl: String,
        mediaType: String,
        seriesId: String = "",
        seriesTitle: String = "",
        thumbUrl: String = "",
    )
    suspend fun delete(bookId: String)
    suspend fun getByBookId(bookId: String): Download?
}
