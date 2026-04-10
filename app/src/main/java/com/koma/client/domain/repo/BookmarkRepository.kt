package com.koma.client.domain.repo

import com.koma.client.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    suspend fun add(bookId: String, page: Int?, locator: String?, note: String?): Bookmark
    suspend fun delete(id: String)
    fun getByBookId(bookId: String): Flow<List<Bookmark>>
    fun getAll(): Flow<List<Bookmark>>
}
