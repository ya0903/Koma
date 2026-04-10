package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.koma.client.data.db.entity.CachedLibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryDao {

    @Query("SELECT * FROM cached_libraries WHERE serverId = :serverId ORDER BY name")
    abstract fun observeByServer(serverId: String): Flow<List<CachedLibraryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(libraries: List<CachedLibraryEntity>)

    @Query("DELETE FROM cached_libraries WHERE serverId = :serverId")
    abstract suspend fun deleteByServer(serverId: String)

    @Transaction
    open suspend fun replaceAll(serverId: String, libraries: List<CachedLibraryEntity>) {
        deleteByServer(serverId)
        insertAll(libraries)
    }
}
