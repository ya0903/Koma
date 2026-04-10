package com.koma.client.data.repo

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.koma.client.data.db.dao.DownloadDao
import com.koma.client.data.db.entity.DownloadEntity
import com.koma.client.domain.model.Download
import com.koma.client.domain.model.DownloadState
import com.koma.client.domain.repo.DownloadRepository
import com.koma.client.work.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    @ApplicationContext private val context: Context,
) : DownloadRepository {

    override fun observeAll(): Flow<List<Download>> =
        downloadDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun enqueue(
        bookId: String,
        serverId: String,
        title: String,
        fileUrl: String,
        mediaType: String,
        seriesId: String,
        seriesTitle: String,
        thumbUrl: String,
    ) {
        val entity = DownloadEntity(
            bookId = bookId,
            serverId = serverId,
            bookTitle = title,
            seriesId = seriesId,
            seriesTitle = seriesTitle,
            thumbUrl = thumbUrl,
            state = "QUEUED",
            fileUrl = fileUrl,
            mediaType = mediaType,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        downloadDao.upsert(entity)

        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_BOOK_ID, bookId)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag("download:$bookId")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    override suspend fun delete(bookId: String) {
        val entity = downloadDao.getByBookId(bookId)
        entity?.filePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        downloadDao.delete(bookId)
        WorkManager.getInstance(context).cancelAllWorkByTag("download:$bookId")
    }

    override suspend fun getByBookId(bookId: String): Download? =
        downloadDao.getByBookId(bookId)?.toDomain()

    private fun DownloadEntity.toDomain() = Download(
        bookId = bookId,
        serverId = serverId,
        bookTitle = bookTitle,
        seriesId = seriesId,
        seriesTitle = seriesTitle,
        thumbUrl = thumbUrl,
        state = state.toDownloadState(),
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        filePath = filePath,
        mediaType = mediaType,
        error = error,
    )

    private fun String.toDownloadState(): DownloadState = when (this) {
        "QUEUED" -> DownloadState.QUEUED
        "DOWNLOADING" -> DownloadState.DOWNLOADING
        "COMPLETE" -> DownloadState.COMPLETE
        "FAILED" -> DownloadState.FAILED
        else -> DownloadState.FAILED
    }
}
