package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.koma.client.data.db.entity.CachedSeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SeriesDao {

    @Query("SELECT * FROM cached_series WHERE libraryId = :libraryId ORDER BY title")
    abstract fun observeByLibrary(libraryId: String): Flow<List<CachedSeriesEntity>>

    @Query("SELECT * FROM cached_series WHERE id = :id")
    abstract suspend fun getById(id: String): CachedSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(series: List<CachedSeriesEntity>)

    @Query("DELETE FROM cached_series WHERE libraryId = :libraryId")
    abstract suspend fun deleteByLibrary(libraryId: String)

    @Transaction
    open suspend fun replaceByLibrary(libraryId: String, series: List<CachedSeriesEntity>) {
        deleteByLibrary(libraryId)
        insertAll(series)
    }
}
