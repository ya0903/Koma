package com.koma.client.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
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
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
            // For JWT-based servers (Kavita), authenticate with the real server ID
            // so the token is stored under the correct key
            if (s.selectedType == MediaServerType.KAVITA) {
                val server = serverRepo.getById(id)
                if (server != null) {
                    val mediaServer = factory.create(s.selectedType, id, url, s.username, s.password)
                    mediaServer.authenticate()
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    onServerAdded: () -> Unit,
    viewModel: AddServerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onServerAdded()
    }

    val filledFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
    val fieldShape = RoundedCornerShape(16.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.add_server_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.add_server_connect_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Segmented server type selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        MediaServerType.entries.forEach { type ->
                            val selected = state.selectedType == type
                            val label = when (type) {
                                MediaServerType.KOMGA -> stringResource(R.string.label_komga)
                                MediaServerType.KAVITA -> stringResource(R.string.label_kavita)
                                MediaServerType.CALIBRE_WEB -> stringResource(R.string.label_calibre_web)
                                MediaServerType.OPDS -> stringResource(R.string.label_opds)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                    )
                                    .clickable { viewModel.selectType(type) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Server URL
                    TextField(
                        value = state.url,
                        onValueChange = viewModel::updateUrl,
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = filledFieldColors,
                        singleLine = true,
                        label = { Text(stringResource(R.string.add_server_url_label)) },
                        placeholder = { Text(stringResource(R.string.add_server_url_hint)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_server),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )

                    // Username
                    TextField(
                        value = state.username,
                        onValueChange = viewModel::updateUsername,
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = filledFieldColors,
                        singleLine = true,
                        label = { Text(stringResource(R.string.add_server_username_label)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )

                    // Password
                    TextField(
                        value = state.password,
                        onValueChange = viewModel::updatePassword,
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = filledFieldColors,
                        singleLine = true,
                        label = { Text(stringResource(R.string.add_server_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )

                    // Display Name (optional)
                    TextField(
                        value = state.displayName,
                        onValueChange = viewModel::updateDisplayName,
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = filledFieldColors,
                        singleLine = true,
                        label = { Text(stringResource(R.string.add_server_name_label)) },
                    )
                }
            }

            // Status feedback card
            state.testResult?.let { msg ->
                val isSuccess = state.testSuccess
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSuccess)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = state.url.isNotBlank() && state.username.isNotBlank() && !state.isTesting,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.add_server_test_button))
                }

                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = state.url.isNotBlank() && state.username.isNotBlank(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(stringResource(R.string.add_server_save_connect_button))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
