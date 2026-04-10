package com.koma.client.domain.repo

import com.koma.client.domain.model.ReadProgress

interface ReadProgressRepository {
    suspend fun get(bookId: String): ReadProgress?
    suspend fun save(bookId: String, page: Int, completed: Boolean)
    suspend fun syncDirty(syncFn: suspend (ReadProgress) -> Unit)
}
