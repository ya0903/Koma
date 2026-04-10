package com.koma.client.domain.repo

import com.koma.client.domain.model.Server
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    fun observeServers(): Flow<List<Server>>
    suspend fun getActive(): Server?
    suspend fun getById(id: String): Server?
    suspend fun insert(server: Server, username: String, password: String)
    /** Update only the name and URL of an existing server, leaving credentials intact. */
    suspend fun update(server: Server)
    suspend fun delete(serverId: String)
    suspend fun setActive(serverId: String)
}
