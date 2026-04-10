package com.koma.client.data.server

import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.server.calibreweb.CalibreWebMediaServer
import com.koma.client.data.server.kavita.KavitaMediaServer
import com.koma.client.data.server.komga.KomgaMediaServer
import com.koma.client.data.server.opds.OpdsMediaServer
import com.koma.client.di.BaseOkHttpClient
import com.koma.client.domain.model.Server
import com.koma.client.domain.server.MediaServer
import com.koma.client.domain.server.MediaServerType
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaServerRegistry @Inject constructor(
    private val credentialStore: CredentialStore,
    @BaseOkHttpClient private val baseOkHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val cache = mutableMapOf<String, MediaServer>()

    fun get(server: Server): MediaServer = cache.getOrPut(server.id) {
        when (server.type) {
            MediaServerType.KOMGA -> KomgaMediaServer(
                id = server.id,
                baseUrl = server.baseUrl,
                credentialStore = credentialStore,
                baseOkHttpClient = baseOkHttpClient,
                json = json,
            )
            MediaServerType.KAVITA -> KavitaMediaServer(
                id = server.id,
                baseUrl = server.baseUrl,
                credentialStore = credentialStore,
                baseOkHttpClient = baseOkHttpClient,
                json = json,
            )
            MediaServerType.CALIBRE_WEB -> CalibreWebMediaServer(
                id = server.id,
                baseUrl = server.baseUrl,
                credentialStore = credentialStore,
                baseOkHttpClient = baseOkHttpClient,
                json = json,
            )
            MediaServerType.OPDS -> OpdsMediaServer(
                id = server.id,
                baseUrl = server.baseUrl,
                credentialStore = credentialStore,
                baseOkHttpClient = baseOkHttpClient,
            )
        }
    }

    fun evict(serverId: String) {
        cache.remove(serverId)
    }

    fun evictAll() {
        cache.clear()
    }
}
