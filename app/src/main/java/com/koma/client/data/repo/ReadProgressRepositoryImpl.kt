package com.koma.client.data.repo

import com.koma.client.data.db.dao.ReadProgressDao
import com.koma.client.data.db.entity.ReadProgressEntity
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.repo.ReadProgressRepository
import javax.inject.Inject

class ReadProgressRepositoryImpl @Inject constructor(
    private val dao: ReadProgressDao,
) : ReadProgressRepository {

    override suspend fun get(bookId: String): ReadProgress? {
        return dao.getByBookId(bookId)?.let {
            ReadProgress(
                bookId = it.bookId,
                page = it.page,
                completed = it.completed,
                locator = it.locator,
                updatedAtEpochMs = it.updatedAtEpochMs,
            )
        }
    }

    override suspend fun save(bookId: String, page: Int, completed: Boolean) {
        dao.upsert(
            ReadProgressEntity(
                bookId = bookId,
                page = page,
                completed = completed,
                locator = null,
                updatedAtEpochMs = System.currentTimeMillis(),
                dirty = true,
            )
        )
    }

    override suspend fun syncDirty(syncFn: suspend (ReadProgress) -> Unit) {
        val dirty = dao.getDirty()
        for (entity in dirty) {
            val progress = ReadProgress(
                bookId = entity.bookId,
                page = entity.page,
                completed = entity.completed,
                locator = entity.locator,
                updatedAtEpochMs = entity.updatedAtEpochMs,
            )
            try {
                syncFn(progress)
                dao.markClean(entity.bookId)
            } catch (_: Exception) {
                // Will retry next sync cycle
            }
        }
    }
}
