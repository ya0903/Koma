package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.koma.client.data.db.entity.ReadProgressEntity

@Dao
interface ReadProgressDao {

    @Query("SELECT * FROM read_progress WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): ReadProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadProgressEntity)

    @Query("SELECT * FROM read_progress WHERE dirty = 1")
    suspend fun getDirty(): List<ReadProgressEntity>

    @Query("UPDATE read_progress SET dirty = 0 WHERE bookId = :bookId")
    suspend fun markClean(bookId: String)
}
