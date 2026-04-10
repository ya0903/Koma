package com.koma.client.ui.server

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.Server
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.MediaServerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerTabUiState(
    val loading: Boolean = true,
    val allServers: List<Server> = emptyList(),
    val activeServerId: String? = null,
    val libraries: List<Library> = emptyList(),
    val error: String? = null,
    // Edit dialog state
    val editingServerId: String? = null,
    val editName: String = "",
    val editUrl: String = "",
    val saving: Boolean = false,
)

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerTabUiState())
    val state: StateFlow<ServerTabUiState> = _state.asStateFlow()

    init {
        observeServers()
    }

    private fun observeServers() {
        viewModelScope.launch {
            serverRepo.observeServers().collect { servers ->
                val activeId = servers.firstOrNull { it.isActive }?.id
                    ?: _state.value.activeServerId
                _state.update { it.copy(loading = false, allServers = servers, activeServerId = activeId) }
                // Reload libraries whenever the active server changes
                val activeServer = servers.firstOrNull { it.id == activeId }
                if (activeServer != null) {
                    loadLibraries(activeServer)
                } else {
                    _state.update { it.copy(libraries = emptyList()) }
                }
            }
        }
    }

    private suspend fun loadLibraries(server: Server) {
        try {
            val mediaServer = registry.get(server)
            val libraries = mediaServer.libraries().first()
            _state.update { it.copy(libraries = libraries) }
        } catch (_: Exception) {
            // Libraries load failure is non-fatal
            _state.update { it.copy(libraries = emptyList()) }
        }
    }

    fun switchTo(serverId: String) {
        viewModelScope.launch {
            serverRepo.setActive(serverId)
            _state.update { it.copy(activeServerId = serverId) }
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            serverRepo.delete(serverId)
            registry.evict(serverId)
        }
    }

    fun startEditing(server: Server) {
        _state.update {
            it.copy(
                editingServerId = server.id,
                editName = server.name,
                editUrl = server.baseUrl,
            )
        }
    }

    fun cancelEditing() {
        _state.update { it.copy(editingServerId = null) }
    }

    fun updateEditName(name: String) {
        _state.update { it.copy(editName = name) }
    }

    fun updateEditUrl(url: String) {
        _state.update { it.copy(editUrl = url) }
    }

    fun saveChanges() {
        val s = _state.value
        val server = s.allServers.firstOrNull { it.id == s.editingServerId } ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val updatedServer = server.copy(
                    name = s.editName.ifBlank { server.name },
                    baseUrl = normalizeUrl(s.editUrl.ifBlank { server.baseUrl }),
                )
                // Use update() to preserve credentials — insert() would overwrite them with empty strings
                serverRepo.update(updatedServer)
                registry.evict(server.id)
                _state.update { it.copy(saving = false, editingServerId = null) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message) }
            }
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

// ─── UI ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    onSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Servers") })
        },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Add New Server button ──
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onSwitchServer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Add New Server")
                }
            }

            val activeServer = state.allServers.firstOrNull { it.id == state.activeServerId }
            val otherServers = state.allServers.filter { it.id != state.activeServerId }

            // ── Active Server section ──
            if (activeServer != null) {
                item {
                    SectionHeader("Active Server")
                }
                item {
                    ActiveServerCard(
                        server = activeServer,
                        libraries = state.libraries,
                        isEditing = state.editingServerId == activeServer.id,
                        editName = state.editName,
                        editUrl = state.editUrl,
                        saving = state.saving,
                        onEdit = { viewModel.startEditing(activeServer) },
                        onRemove = { deleteConfirmId = activeServer.id },
                        onEditNameChange = viewModel::updateEditName,
                        onEditUrlChange = viewModel::updateEditUrl,
                        onSave = { viewModel.saveChanges() },
                        onCancelEdit = { viewModel.cancelEditing() },
                    )
                }
            }

            // ── Other Servers section ──
            if (otherServers.isNotEmpty()) {
                item { SectionHeader("Other Servers") }
                items(otherServers, key = { it.id }) { server ->
                    OtherServerCard(
                        server = server,
                        isEditing = state.editingServerId == server.id,
                        editName = state.editName,
                        editUrl = state.editUrl,
                        saving = state.saving,
                        onSwitchTo = { viewModel.switchTo(server.id) },
                        onEdit = { viewModel.startEditing(server) },
                        onRemove = { deleteConfirmId = server.id },
                        onEditNameChange = viewModel::updateEditName,
                        onEditUrlChange = viewModel::updateEditUrl,
                        onSave = { viewModel.saveChanges() },
                        onCancelEdit = { viewModel.cancelEditing() },
                    )
                }
            }

            // ── Empty state ──
            if (state.allServers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No servers configured yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Active server details section ──
            if (activeServer != null && state.libraries.isNotEmpty()) {
                item { SectionHeader("Active Server Details") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "${state.libraries.size} librar${if (state.libraries.size == 1) "y" else "ies"} available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            state.libraries.forEach { library ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(library.name, style = MaterialTheme.typography.bodyMedium)
                                    if (library.unreadCount > 0) {
                                        Text(
                                            "${library.unreadCount} unread",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Reading Stats", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            StatRow("Books read", "--")
                            StatRow("Series in progress", "--")
                            StatRow("Total reading time", "Coming soon")
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Management", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            StatRow("Logs", "Coming soon")
                            StatRow("History", "Coming soon")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // Delete confirmation dialog
    deleteConfirmId?.let { serverId ->
        val serverName = state.allServers.firstOrNull { it.id == serverId }?.name ?: "this server"
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Remove Server") },
            text = { Text("Remove \"$serverName\"? You can add it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteServer(serverId)
                    deleteConfirmId = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") }
            },
        )
    }

    // Edit dialog (for editing server details inline)
    if (state.editingServerId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelEditing() },
            title = { Text("Edit Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.editName,
                        onValueChange = viewModel::updateEditName,
                        label = { Text("Server Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.editUrl,
                        onValueChange = viewModel::updateEditUrl,
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveChanges() },
                    enabled = !state.saving,
                ) { Text(if (state.saving) "Saving…" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelEditing() }) { Text("Cancel") }
            },
        )
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TypeBadge(type: MediaServerType) {
    val label = when (type) {
        MediaServerType.KOMGA -> "Komga"
        MediaServerType.KAVITA -> "Kavita"
        MediaServerType.CALIBRE_WEB -> "Calibre-Web"
    }
    SuggestionChip(onClick = {}, label = { Text(label) })
}

@Composable
private fun ActiveServerCard(
    server: Server,
    libraries: List<Library>,
    isEditing: Boolean,
    editName: String,
    editUrl: String,
    saving: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onEditNameChange: (String) -> Unit,
    onEditUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        server.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TypeBadge(server.type)
                if (libraries.isNotEmpty()) {
                    Text(
                        "${libraries.size} librar${if (libraries.size == 1) "y" else "ies"} · Connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun OtherServerCard(
    server: Server,
    isEditing: Boolean,
    editName: String,
    editUrl: String,
    saving: Boolean,
    onSwitchTo: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onEditNameChange: (String) -> Unit,
    onEditUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        server.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TypeBadge(server.type)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSwitchTo, modifier = Modifier.weight(1f)) {
                    Text("Switch to this")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
