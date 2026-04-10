package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.koma.client.data.db.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ServerDao {

    @Query("SELECT * FROM servers ORDER BY name")
    abstract fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    abstract suspend fun getById(id: String): ServerEntity?

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    abstract suspend fun getActive(): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("UPDATE servers SET isActive = 0")
    abstract suspend fun deactivateAll()

    @Query("UPDATE servers SET isActive = 1 WHERE id = :id")
    abstract suspend fun activateOne(id: String)

    @Query("UPDATE servers SET name = :name, baseUrl = :baseUrl WHERE id = :id")
    abstract suspend fun updateNameAndUrl(id: String, name: String, baseUrl: String)

    @Transaction
    open suspend fun setActive(id: String) {
        deactivateAll()
        activateOne(id)
    }
}
