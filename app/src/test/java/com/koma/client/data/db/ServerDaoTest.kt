package com.koma.client.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.koma.client.data.db.entity.ServerEntity
import com.koma.client.domain.server.MediaServerType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ServerDaoTest {

    private lateinit var db: KomaDatabase
    private lateinit var dao: com.koma.client.data.db.dao.ServerDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KomaDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.serverDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_then_observe_returns_server() = runTest {
        val server = ServerEntity(
            id = "s1",
            type = MediaServerType.KOMGA,
            name = "Home Komga",
            baseUrl = "https://komga.local",
            username = "user",
            encPassword = "enc",
            encJwtToken = null,
            encSessionCookie = null,
            isActive = true,
            lastSyncAtEpochMs = 0L,
        )
        dao.insert(server)

        dao.observeAll().test {
            val emitted = awaitItem()
            assertThat(emitted).hasSize(1)
            assertThat(emitted[0].name).isEqualTo("Home Komga")
            assertThat(emitted[0].type).isEqualTo(MediaServerType.KOMGA)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emptyDatabase_emits_empty_list() = runTest {
        dao.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delete_removes_server() = runTest {
        val server = ServerEntity(
            id = "s1",
            type = MediaServerType.KAVITA,
            name = "Kavita",
            baseUrl = "https://kavita.local",
            username = "u",
            encPassword = "p",
            encJwtToken = null,
            encSessionCookie = null,
            isActive = false,
            lastSyncAtEpochMs = 0L,
        )
        dao.insert(server)
        dao.delete("s1")
        dao.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
