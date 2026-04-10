package com.koma.client.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.koma.client.R
import com.koma.client.domain.repo.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HomeState {
    data object Empty : HomeState
    data class HasServers(val count: Int) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: ServerRepository,
) : ViewModel() {

    val state: StateFlow<HomeState> = repo.observeServers()
        .map { list -> if (list.isEmpty()) HomeState.Empty else HomeState.HasServers(list.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Empty)
}

@Composable
fun HomeScreen(
    onAddServer: () -> Unit,
    onGoToLibraries: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            HomeState.Empty -> {
                Text(
                    text = stringResource(R.string.home_no_servers_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.home_no_servers_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onAddServer, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.home_add_server))
                }
            }
            is HomeState.HasServers -> {
                LaunchedEffect(Unit) {
                    onGoToLibraries()
                }
            }
        }
    }
}
