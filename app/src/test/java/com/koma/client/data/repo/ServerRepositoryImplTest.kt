package com.koma.client.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.koma.client.data.auth.CoilAuthInterceptor
import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.db.KomaDatabase
import com.koma.client.data.db.entity.ServerEntity
import com.koma.client.domain.model.Server
import com.koma.client.domain.server.MediaServerType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * No-op CredentialStore for unit tests — avoids Android Keystore dependency.
 * EncryptedSharedPreferences requires real Keystore hardware which is unavailable
 * in the Robolectric JVM sandbox.
 */
private class FakeCredentialStore : CredentialStore(null) {
    private val data = mutableMapOf<String, Pair<String, String>>()
    override fun getUsername(serverId: String): String? = data[serverId]?.first
    override fun getPassword(serverId: String): String? = data[serverId]?.second
    override fun store(serverId: String, username: String, password: String) {
        data[serverId] = username to password
    }
    override fun delete(serverId: String) { data.remove(serverId) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ServerRepositoryImplTest {

    private lateinit var db: KomaDatabase
    private lateinit var repo: ServerRepositoryImpl

    @Before
    fun setUp() {
        val fakeCredentialStore = FakeCredentialStore()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KomaDatabase::class.java,
        ).allowMainThreadQueries().build()
        // CoilAuthInterceptor uses the FakeCredentialStore; setActiveServer is a no-op in tests
        repo = ServerRepositoryImpl(db.serverDao(), fakeCredentialStore, CoilAuthInterceptor(fakeCredentialStore))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeServers_empty_by_default() = runTest {
        repo.observeServers().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeServers_emits_after_insert() = runTest {
        repo.observeServers().test {
            assertThat(awaitItem()).isEmpty()

            db.serverDao().insert(
                ServerEntity(
                    id = "s1",
                    type = MediaServerType.KOMGA,
                    name = "K",
                    baseUrl = "https://k",
                    username = "u",
                    encPassword = "p",
                    encJwtToken = null,
                    encSessionCookie = null,
                    isActive = true,
                    lastSyncAtEpochMs = 0L,
                )
            )
            val next = awaitItem()
            assertThat(next).hasSize(1)
            val server: Server = next[0]
            assertThat(server.id).isEqualTo("s1")
            assertThat(server.name).isEqualTo("K")
            assertThat(server.type).isEqualTo(MediaServerType.KOMGA)
            assertThat(server.baseUrl).isEqualTo("https://k")
            assertThat(server.isActive).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
