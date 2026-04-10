package com.koma.client.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.db.dao.DownloadDao
import com.koma.client.data.db.entity.DownloadEntity
import com.koma.client.di.BaseOkHttpClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val credentialStore: CredentialStore,
    @BaseOkHttpClient private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_BOOK_ID = "bookId"
    }

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()

        val entity = downloadDao.getByBookId(bookId) ?: return Result.failure()

        // Mark downloading
        downloadDao.upsert(
            entity.copy(
                state = "DOWNLOADING",
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )

        return try {
            val ext = when (entity.mediaType.uppercase()) {
                "EPUB" -> "epub"
                "PDF" -> "pdf"
                else -> "cbz"
            }
            val destDir = File(context.filesDir, "downloads").also { it.mkdirs() }
            val destFile = File(destDir, "$bookId.$ext")

            val requestBuilder = Request.Builder().url(entity.fileUrl)

            // Add basic auth if credentials exist
            val username = credentialStore.getUsername(entity.serverId)
            val password = credentialStore.getPassword(entity.serverId)
            val jwtToken = credentialStore.getJwtToken(entity.serverId)
            when {
                jwtToken != null -> requestBuilder.header("Authorization", "Bearer $jwtToken")
                username != null && password != null -> {
                    val credential = okhttp3.Credentials.basic(username, password)
                    requestBuilder.header("Authorization", credential)
                }
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                downloadDao.upsert(
                    entity.copy(
                        state = "FAILED",
                        error = "HTTP ${response.code}",
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                return Result.failure()
            }

            val body = response.body ?: run {
                downloadDao.upsert(
                    entity.copy(
                        state = "FAILED",
                        error = "Empty response body",
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                return Result.failure()
            }

            val totalBytes = body.contentLength().takeIf { it > 0 } ?: 0L
            var bytesDownloaded = 0L

            destFile.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var lastProgressUpdate = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        bytesDownloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 500) {
                            lastProgressUpdate = now
                            downloadDao.upsert(
                                entity.copy(
                                    state = "DOWNLOADING",
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes,
                                    updatedAtEpochMs = now,
                                ),
                            )
                        }
                    }
                }
            }

            downloadDao.upsert(
                entity.copy(
                    state = "COMPLETE",
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = bytesDownloaded,
                    filePath = destFile.absolutePath,
                    error = null,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )

            Result.success()
        } catch (e: Exception) {
            downloadDao.upsert(
                entity.copy(
                    state = "FAILED",
                    error = e.message ?: "Unknown error",
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            Result.failure()
        }
    }
}
