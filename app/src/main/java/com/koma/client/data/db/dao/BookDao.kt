package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.koma.client.data.db.entity.CachedBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookDao {

    @Query("SELECT * FROM cached_books WHERE seriesId = :seriesId ORDER BY number")
    abstract fun observeBySeries(seriesId: String): Flow<List<CachedBookEntity>>

    @Query("SELECT * FROM cached_books WHERE id = :id")
    abstract suspend fun getById(id: String): CachedBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(books: List<CachedBookEntity>)

    @Query("DELETE FROM cached_books WHERE seriesId = :seriesId")
    abstract suspend fun deleteBySeries(seriesId: String)

    @Transaction
    open suspend fun replaceBySeries(seriesId: String, books: List<CachedBookEntity>) {
        deleteBySeries(seriesId)
        insertAll(books)
    }
}
