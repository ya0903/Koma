package com.koma.client.data.repo

import com.koma.client.data.db.dao.BookmarkDao
import com.koma.client.data.db.entity.BookmarkEntity
import com.koma.client.domain.model.Bookmark
import com.koma.client.domain.repo.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarkRepository {

    override suspend fun add(
        bookId: String,
        page: Int?,
        locator: String?,
        note: String?,
    ): Bookmark {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = BookmarkEntity(
            id = id,
            bookId = bookId,
            page = page,
            locator = locator,
            note = note,
            createdAtEpochMs = now,
        )
        dao.insert(entity)
        return Bookmark(
            id = id,
            bookId = bookId,
            page = page,
            locator = locator,
            note = note,
            createdAtEpochMs = now,
        )
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override fun getByBookId(bookId: String): Flow<List<Bookmark>> =
        dao.getByBookId(bookId).map { list ->
            list.map { it.toDomain() }
        }

    override fun getAll(): Flow<List<Bookmark>> =
        dao.getAll().map { list ->
            list.map { it.toDomain() }
        }

    private fun BookmarkEntity.toDomain() = Bookmark(
        id = id,
        bookId = bookId,
        page = page,
        locator = locator,
        note = note,
        createdAtEpochMs = createdAtEpochMs,
    )
}
