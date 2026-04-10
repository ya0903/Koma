package com.koma.client.ui.auth

import com.google.common.truth.Truth.assertThat
import com.koma.client.data.auth.CredentialStore
import com.koma.client.domain.model.Server
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.MediaServer
import com.koma.client.domain.server.MediaServerFactory
import com.koma.client.domain.server.MediaServerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class AddServerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private class FakeRepo : ServerRepository {
        val servers = mutableListOf<Server>()
        private val _flow = MutableStateFlow<List<Server>>(emptyList())
        override fun observeServers(): Flow<List<Server>> = _flow
        override suspend fun getActive() = servers.firstOrNull { it.isActive }
        override suspend fun getById(id: String) = servers.firstOrNull { it.id == id }
        override suspend fun insert(server: Server, username: String, password: String) {
            servers.add(server)
            _flow.value = servers.toList()
        }
        override suspend fun update(server: Server) {
            val idx = servers.indexOfFirst { it.id == server.id }
            if (idx >= 0) servers[idx] = server
            _flow.value = servers.toList()
        }
        override suspend fun delete(serverId: String) {
            servers.removeAll { it.id == serverId }
            _flow.value = servers.toList()
        }
        override suspend fun setActive(serverId: String) {}
    }

    private class FakeCredentialStore : CredentialStore(null) {
        private val data = mutableMapOf<String, Pair<String, String>>()
        override fun getUsername(serverId: String): String? = data[serverId]?.first
        override fun getPassword(serverId: String): String? = data[serverId]?.second
        override fun store(serverId: String, username: String, password: String) {
            data[serverId] = username to password
        }
        override fun delete(serverId: String) { data.remove(serverId) }
    }

    @Test
    fun initial_state_has_komga_selected() = runTest {
        val vm = AddServerViewModel(FakeRepo(), FakeMediaServerFactory(), FakeCredentialStore())
        assertThat(vm.uiState.value.selectedType).isEqualTo(MediaServerType.KOMGA)
        assertThat(vm.uiState.value.isTesting).isFalse()
    }

    @Test
    fun save_adds_server_to_repo() = runTest {
        val repo = FakeRepo()
        val vm = AddServerViewModel(repo, FakeMediaServerFactory(), FakeCredentialStore())
        vm.updateUrl("https://komga.local")
        vm.updateUsername("user")
        vm.updatePassword("pass")
        vm.save()
        advanceUntilIdle()
        assertThat(repo.servers).hasSize(1)
        assertThat(repo.servers[0].baseUrl).isEqualTo("https://komga.local")
    }

    /** Fake factory that always returns a successfully authenticating MediaServer */
    private class FakeMediaServerFactory : MediaServerFactory {
        override fun create(
            type: MediaServerType,
            id: String,
            baseUrl: String,
            username: String,
            password: String,
        ): MediaServer = FakeMediaServer(id)
    }

    private class FakeMediaServer(override val id: String) : MediaServer {
        override val type = MediaServerType.KOMGA
        override val capabilities = com.koma.client.domain.server.ServerCapabilities.komgaDefaults()
        override suspend fun authenticate() = Result.success(Unit)
        override fun libraries() = throw NotImplementedError()
        override fun series(libraryId: String, filter: com.koma.client.domain.server.SeriesFilter) = throw NotImplementedError()
        override suspend fun seriesDetail(id: String) = throw NotImplementedError()
        override fun books(seriesId: String) = throw NotImplementedError()
        override suspend fun book(id: String) = throw NotImplementedError()
        override suspend fun pageUrl(bookId: String, page: Int) = ""
        override suspend fun fileUrl(bookId: String) = ""
        override suspend fun thumbnailUrl(id: String, kind: com.koma.client.domain.model.ThumbKind) = ""
        override suspend fun updateProgress(bookId: String, progress: com.koma.client.domain.model.ReadProgress) {}
        override suspend fun search(query: String, filter: com.koma.client.domain.server.SearchFilter) =
            com.koma.client.domain.server.SearchResults(emptyList(), emptyList())
        override suspend fun onDeckBooks() = emptyList<com.koma.client.domain.model.Book>()
        override suspend fun recentlyAddedSeries() = emptyList<com.koma.client.domain.model.Series>()
        override suspend fun recentlyUpdatedSeries() = emptyList<com.koma.client.domain.model.Series>()
        override suspend fun recentlyAddedBooks() = emptyList<com.koma.client.domain.model.Book>()
        override suspend fun recentlyReleasedBooks() = emptyList<com.koma.client.domain.model.Book>()
        override suspend fun recentlyReadBooks() = emptyList<com.koma.client.domain.model.Book>()
        override suspend fun libraryStats(libraryId: String?) =
            com.koma.client.domain.model.LibraryStats(seriesCount = 0, bookCount = 0)
        override suspend fun availableGenres() = emptyList<String>()
        override suspend fun availableTags() = emptyList<String>()
        override suspend fun availablePublishers() = emptyList<String>()
    }
}
