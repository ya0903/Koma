package com.koma.client.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.koma.client.R
import com.koma.client.data.auth.CredentialStore
import com.koma.client.domain.model.Server
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.MediaServerFactory
import com.koma.client.domain.server.MediaServerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddServerUiState(
    val selectedType: MediaServerType = MediaServerType.KOMGA,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val factory: MediaServerFactory,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddServerUiState())
    val uiState: StateFlow<AddServerUiState> = _uiState.asStateFlow()

    fun selectType(type: MediaServerType) = _uiState.update { it.copy(selectedType = type, testResult = null) }
    fun updateUrl(url: String) = _uiState.update { it.copy(url = url, testResult = null) }
    fun updateUsername(u: String) = _uiState.update { it.copy(username = u, testResult = null) }
    fun updatePassword(p: String) = _uiState.update { it.copy(password = p, testResult = null) }
    fun updateDisplayName(n: String) = _uiState.update { it.copy(displayName = n) }

    fun testConnection() {
        val s = _uiState.value
        val url = normalizeUrl(s.url)
        _uiState.update { it.copy(isTesting = true, testResult = null, url = url) }
        viewModelScope.launch {
            val server = factory.create(s.selectedType, "test", url, s.username, s.password)
            val result = server.authenticate()
            // Clean up transient test credentials so they don't persist in EncryptedSharedPreferences
            credentialStore.delete("test")
            _uiState.update {
                it.copy(
                    isTesting = false,
                    testSuccess = result.isSuccess,
                    testResult = if (result.isSuccess) "Connected!" else "Failed: ${result.exceptionOrNull()?.message}",
                )
            }
        }
    }

    fun save() {
        val s = _uiState.value
        val url = normalizeUrl(s.url)
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val name = s.displayName.ifBlank { "${s.selectedType.name} - $url" }
            serverRepo.insert(
                Server(id = id, type = s.selectedType, name = name, baseUrl = url, isActive = true),
                s.username,
                s.password,
            )
            serverRepo.setActive(id)
            _uiState.update { it.copy(saved = true) }
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }
}

@Composable
fun AddServerScreen(
    onServerAdded: () -> Unit,
    viewModel: AddServerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onServerAdded()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.add_server_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        // Type selector
        Text(stringResource(R.string.add_server_type_label), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaServerType.entries.forEach { type ->
                FilterChip(
                    selected = state.selectedType == type,
                    onClick = { viewModel.selectType(type) },
                    label = {
                        Text(when (type) {
                            MediaServerType.KOMGA -> stringResource(R.string.label_komga)
                            MediaServerType.KAVITA -> stringResource(R.string.label_kavita)
                            MediaServerType.CALIBRE_WEB -> stringResource(R.string.label_calibre_web)
                        })
                    },
                )
            }
        }

        OutlinedTextField(
            value = state.url,
            onValueChange = viewModel::updateUrl,
            label = { Text(stringResource(R.string.add_server_url_label)) },
            placeholder = { Text(stringResource(R.string.add_server_url_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            label = { Text(stringResource(R.string.add_server_username_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            label = { Text(stringResource(R.string.add_server_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::updateDisplayName,
            label = { Text(stringResource(R.string.add_server_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = state.url.isNotBlank() && state.username.isNotBlank() && !state.isTesting,
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.add_server_test_button))
            }

            Button(
                onClick = viewModel::save,
                enabled = state.url.isNotBlank() && state.username.isNotBlank(),
            ) {
                Text(stringResource(R.string.add_server_save_button))
            }
        }

        state.testResult?.let { msg ->
            Text(
                text = msg,
                color = if (state.testSuccess) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
            )
        }
    }
}
