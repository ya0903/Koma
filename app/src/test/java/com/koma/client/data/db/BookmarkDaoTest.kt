package com.koma.client.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.koma.client.data.db.entity.BookmarkEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BookmarkDaoTest {

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
    fun insert_and_getByBookId_returns_bookmark() = runTest {
        val dao = db.bookmarkDao()
        val entity = BookmarkEntity(
            id = "bm1",
            bookId = "book1",
            page = 5,
            locator = null,
            note = "Great scene",
            createdAtEpochMs = 1000L,
        )
        dao.insert(entity)

        dao.getByBookId("book1").test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].id).isEqualTo("bm1")
            assertThat(list[0].page).isEqualTo(5)
            assertThat(list[0].note).isEqualTo("Great scene")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delete_removes_bookmark() = runTest {
        val dao = db.bookmarkDao()
        dao.insert(BookmarkEntity("bm1", "book1", 3, null, null, 1000L))
        dao.delete("bm1")

        dao.getByBookId("book1").test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getByBookId_filters_by_bookId() = runTest {
        val dao = db.bookmarkDao()
        dao.insert(BookmarkEntity("bm1", "book1", 1, null, null, 1000L))
        dao.insert(BookmarkEntity("bm2", "book2", 2, null, null, 2000L))

        dao.getByBookId("book1").test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].bookId).isEqualTo("book1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun insert_replace_updates_existing() = runTest {
        val dao = db.bookmarkDao()
        dao.insert(BookmarkEntity("bm1", "book1", 3, null, "old note", 1000L))
        dao.insert(BookmarkEntity("bm1", "book1", 3, null, "new note", 2000L))

        dao.getByBookId("book1").test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].note).isEqualTo("new note")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
