package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.koma.client.data.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE bookId = :bookId LIMIT 1")
    suspend fun getByBookId(bookId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE bookId = :bookId")
    suspend fun delete(bookId: String)

    @Query("SELECT * FROM downloads WHERE state = 'QUEUED'")
    suspend fun getQueued(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state = 'COMPLETE'")
    suspend fun getComplete(): List<DownloadEntity>
}
