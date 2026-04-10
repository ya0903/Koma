package com.koma.client.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.koma.client.data.db.entity.CachedLibraryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryDaoTest {

    private lateinit var db: KomaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KomaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun replaceAll_clears_and_inserts() = runTest {
        val dao = db.libraryDao()
        val libs = listOf(
            CachedLibraryEntity(id = "l1", serverId = "s1", name = "Manga"),
            CachedLibraryEntity(id = "l2", serverId = "s1", name = "Comics"),
        )
        dao.replaceAll("s1", libs)
        dao.observeByServer("s1").test {
            val items = awaitItem()
            assertThat(items).hasSize(2)
            assertThat(items.map { it.name }).containsExactly("Comics", "Manga")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun replaceAll_scoped_to_server() = runTest {
        val dao = db.libraryDao()
        dao.replaceAll("s1", listOf(CachedLibraryEntity("l1", "s1", "Manga")))
        dao.replaceAll("s2", listOf(CachedLibraryEntity("l2", "s2", "Books")))

        dao.observeByServer("s1").test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
        // Now replace s1 — should not affect s2
        dao.replaceAll("s1", emptyList())
        dao.observeByServer("s2").test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
