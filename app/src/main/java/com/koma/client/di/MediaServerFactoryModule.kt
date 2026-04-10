package com.koma.client.di

import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.server.calibreweb.CalibreWebMediaServer
import com.koma.client.data.server.kavita.KavitaMediaServer
import com.koma.client.data.server.komga.KomgaMediaServer
import com.koma.client.domain.server.MediaServer
import com.koma.client.domain.server.MediaServerFactory
import com.koma.client.domain.server.MediaServerType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaServerFactoryModule {

    @Provides
    @Singleton
    fun provideMediaServerFactory(
        credentialStore: CredentialStore,
        @BaseOkHttpClient baseOkHttpClient: OkHttpClient,
        json: Json,
    ): MediaServerFactory = object : MediaServerFactory {
        override fun create(
            type: MediaServerType,
            id: String,
            baseUrl: String,
            username: String,
            password: String,
        ): MediaServer {
            // Store credentials so the server instance can retrieve them during authenticate().
            // For test connections (id == "test"), the caller is responsible for deleting them
            // after the test completes to avoid leaking into EncryptedSharedPreferences.
            credentialStore.store(id, username, password)
            return when (type) {
                MediaServerType.KOMGA -> KomgaMediaServer(id, baseUrl, credentialStore, baseOkHttpClient, json)
                MediaServerType.KAVITA -> KavitaMediaServer(id, baseUrl, credentialStore, baseOkHttpClient, json)
                MediaServerType.CALIBRE_WEB -> CalibreWebMediaServer(id, baseUrl, credentialStore, baseOkHttpClient, json)
            }
        }
    }
}
