package com.koma.client.ui.reader.image

import com.google.common.truth.Truth.assertThat
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.repo.ReadProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class ImageReaderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private class FakeProgressRepo : ReadProgressRepository {
        val saved = mutableListOf<Triple<String, Int, Boolean>>()
        private var stored: ReadProgress? = null

        override suspend fun get(bookId: String) = stored
        override suspend fun save(bookId: String, page: Int, completed: Boolean) {
            saved.add(Triple(bookId, page, completed))
            stored = ReadProgress(bookId, page, completed)
        }
        override suspend fun syncDirty(syncFn: suspend (ReadProgress) -> Unit) {}
    }

    @Test
    fun initial_state_starts_at_page_zero() = runTest {
        val vm = createViewModel()
        assertThat(vm.uiState.value.currentPage).isEqualTo(0)
    }

    @Test
    fun goToPage_updates_current_page() = runTest {
        val vm = createViewModel(totalPages = 20)
        vm.goToPage(5)
        advanceUntilIdle()
        assertThat(vm.uiState.value.currentPage).isEqualTo(5)
    }

    @Test
    fun goToPage_saves_progress() = runTest {
        val repo = FakeProgressRepo()
        val vm = createViewModel(totalPages = 20, progressRepo = repo)
        vm.goToPage(10)
        advanceUntilIdle()
        assertThat(repo.saved.last().second).isEqualTo(10)
    }

    @Test
    fun goToPage_clamps_to_valid_range() = runTest {
        val vm = createViewModel(totalPages = 10)
        vm.goToPage(15)
        advanceUntilIdle()
        assertThat(vm.uiState.value.currentPage).isEqualTo(9)

        vm.goToPage(-1)
        advanceUntilIdle()
        assertThat(vm.uiState.value.currentPage).isEqualTo(0)
    }

    @Test
    fun toggleOverlay_flips_visibility() = runTest {
        val vm = createViewModel()
        assertThat(vm.uiState.value.overlayVisible).isFalse()
        vm.toggleOverlay()
        assertThat(vm.uiState.value.overlayVisible).isTrue()
        vm.toggleOverlay()
        assertThat(vm.uiState.value.overlayVisible).isFalse()
    }

    private fun createViewModel(
        bookId: String = "b1",
        totalPages: Int = 10,
        progressRepo: ReadProgressRepository = FakeProgressRepo(),
    ): ImageReaderViewModel {
        val pageUrls = (0 until totalPages).map { "https://komga.local/api/v1/books/$bookId/pages/$it" }
        return ImageReaderViewModel(
            bookId = bookId,
            pageUrls = pageUrls,
            bookTitle = "Test Book",
            progressRepo = progressRepo,
        )
    }
}
