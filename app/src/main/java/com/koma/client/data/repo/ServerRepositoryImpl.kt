package com.koma.client.data.repo

import com.koma.client.data.auth.CoilAuthInterceptor
import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.db.dao.ServerDao
import com.koma.client.data.db.entity.ServerEntity
import com.koma.client.domain.model.Server
import com.koma.client.domain.repo.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URL
import javax.inject.Inject

class ServerRepositoryImpl @Inject constructor(
    private val dao: ServerDao,
    private val credentialStore: CredentialStore,
    private val coilAuthInterceptor: CoilAuthInterceptor,
) : ServerRepository {

    override fun observeServers(): Flow<List<Server>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getActive(): Server? = dao.getActive()?.toDomain()

    override suspend fun getById(id: String): Server? = dao.getById(id)?.toDomain()

    override suspend fun insert(server: Server, username: String, password: String) {
        credentialStore.store(server.id, username, password)
        dao.insert(
            ServerEntity(
                id = server.id,
                type = server.type,
                name = server.name,
                baseUrl = server.baseUrl,
                username = username,
                encPassword = "",  // actual password in EncryptedSharedPreferences
                encJwtToken = null,
                encSessionCookie = null,
                isActive = server.isActive,
                lastSyncAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun update(server: Server) {
        dao.updateNameAndUrl(server.id, server.name, server.baseUrl)
    }

    override suspend fun delete(serverId: String) {
        credentialStore.delete(serverId)
        dao.delete(serverId)
    }

    override suspend fun setActive(serverId: String) {
        dao.setActive(serverId)
        // Refresh the Coil auth cache so image requests use the new active server's credentials
        val server = dao.getById(serverId)
        if (server != null) {
            val host = runCatching { URL(server.baseUrl).host }.getOrDefault("")
            if (host.isNotEmpty()) {
                coilAuthInterceptor.setActiveServer(serverId, host)
            }
        }
    }
}

private fun ServerEntity.toDomain() = Server(
    id = id,
    type = type,
    name = name,
    baseUrl = baseUrl,
    isActive = isActive,
)
