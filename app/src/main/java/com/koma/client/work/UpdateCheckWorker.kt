package com.koma.client.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateChecker: UpdateChecker,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            updateChecker.checkForUpdate()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
