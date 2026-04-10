package com.koma.client.ui.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.koma.client.domain.model.Server
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.MediaServerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private class FakeRepo(initial: List<Server> = emptyList()) : ServerRepository {
        private val _flow = MutableStateFlow(initial)
        override fun observeServers() = _flow
        override suspend fun getActive(): Server? = _flow.value.firstOrNull { it.isActive }
        override suspend fun getById(id: String): Server? = _flow.value.firstOrNull { it.id == id }
        override suspend fun insert(server: Server, username: String, password: String) {
            _flow.value = _flow.value + server
        }
        override suspend fun update(server: Server) {
            _flow.value = _flow.value.map { if (it.id == server.id) server else it }
        }
        override suspend fun delete(serverId: String) {
            _flow.value = _flow.value.filter { it.id != serverId }
        }
        override suspend fun setActive(serverId: String) {}
        fun emit(list: List<Server>) { _flow.value = list }
    }

    @Test
    fun initial_state_is_empty() = runTest {
        val vm = HomeViewModel(FakeRepo())
        vm.state.test {
            assertThat(awaitItem()).isEqualTo(HomeState.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emits_hasServers_when_repo_has_servers() = runTest {
        val repo = FakeRepo(
            listOf(
                Server(
                    id = "1",
                    type = MediaServerType.KOMGA,
                    name = "K",
                    baseUrl = "https://k",
                    isActive = true,
                )
            )
        )
        val vm = HomeViewModel(repo)
        vm.state.test {
            // Collect until we see HasServers (initial Empty may arrive first)
            var item = awaitItem()
            if (item == HomeState.Empty) item = awaitItem()
            assertThat(item).isInstanceOf(HomeState.HasServers::class.java)
            assertThat((item as HomeState.HasServers).count).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
