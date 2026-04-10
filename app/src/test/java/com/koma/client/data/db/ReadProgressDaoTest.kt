package com.koma.client.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.koma.client.data.db.entity.ReadProgressEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadProgressDaoTest {

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
    fun upsert_and_get_by_bookId() = runTest {
        val dao = db.readProgressDao()
        val progress = ReadProgressEntity(
            bookId = "b1",
            page = 10,
            completed = false,
            locator = null,
            updatedAtEpochMs = 1000L,
            dirty = true,
        )
        dao.upsert(progress)
        val result = dao.getByBookId("b1")
        assertThat(result).isNotNull()
        assertThat(result!!.page).isEqualTo(10)
        assertThat(result.dirty).isTrue()
    }

    @Test
    fun upsert_overwrites_existing() = runTest {
        val dao = db.readProgressDao()
        dao.upsert(ReadProgressEntity("b1", 5, false, null, 100L, true))
        dao.upsert(ReadProgressEntity("b1", 15, false, null, 200L, true))
        val result = dao.getByBookId("b1")
        assertThat(result!!.page).isEqualTo(15)
    }

    @Test
    fun getDirty_returns_only_dirty() = runTest {
        val dao = db.readProgressDao()
        dao.upsert(ReadProgressEntity("b1", 5, false, null, 100L, dirty = true))
        dao.upsert(ReadProgressEntity("b2", 10, false, null, 100L, dirty = false))
        val dirty = dao.getDirty()
        assertThat(dirty).hasSize(1)
        assertThat(dirty[0].bookId).isEqualTo("b1")
    }

    @Test
    fun markClean_clears_dirty_flag() = runTest {
        val dao = db.readProgressDao()
        dao.upsert(ReadProgressEntity("b1", 5, false, null, 100L, dirty = true))
        dao.markClean("b1")
        val result = dao.getByBookId("b1")
        assertThat(result!!.dirty).isFalse()
    }
}
