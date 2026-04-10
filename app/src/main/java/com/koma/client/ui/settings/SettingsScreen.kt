package com.koma.client.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.imageLoader
import com.koma.client.ui.reader.common.FitMode
import com.koma.client.ui.reader.common.PageLayout
import com.koma.client.ui.reader.common.ReaderPreferences
import com.koma.client.ui.reader.common.ReadingDirection
import com.koma.client.work.UpdateCheckWorker
import com.koma.client.work.UpdateChecker
import com.koma.client.work.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

val Context.appPrefsDataStore by preferencesDataStore(name = "app_prefs")

object AppPrefsKeys {
    val THEME = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsUiState(
    val fitMode: FitMode = FitMode.WIDTH,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val pageLayout: PageLayout = PageLayout.SINGLE,
    val keepScreenOn: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoUpdateCheck: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val readerPrefs: ReaderPreferences,
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    val appContext: Context = context

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    val state: StateFlow<SettingsUiState> = combine(
        readerPrefs.fitMode,
        readerPrefs.readingDirection,
        readerPrefs.pageLayout,
        readerPrefs.keepScreenOn,
        context.appPrefsDataStore.data.map { prefs ->
            val theme = prefs[AppPrefsKeys.THEME]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
            val dynamic = prefs[AppPrefsKeys.DYNAMIC_COLOR] ?: true
            val autoUpdate = prefs[AppPrefsKeys.AUTO_UPDATE_CHECK] ?: false
            Triple(theme, dynamic, autoUpdate)
        },
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val fitMode = values[0] as FitMode
        @Suppress("UNCHECKED_CAST")
        val direction = values[1] as ReadingDirection
        @Suppress("UNCHECKED_CAST")
        val layout = values[2] as PageLayout
        @Suppress("UNCHECKED_CAST")
        val screenOn = values[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val appPrefs = values[4] as Triple<ThemeMode, Boolean, Boolean>
        SettingsUiState(
            fitMode = fitMode,
            readingDirection = direction,
            pageLayout = layout,
            keepScreenOn = screenOn,
            themeMode = appPrefs.first,
            dynamicColor = appPrefs.second,
            autoUpdateCheck = appPrefs.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setFitMode(mode: FitMode) {
        viewModelScope.launch { readerPrefs.setFitMode(mode) }
    }

    fun setReadingDirection(dir: ReadingDirection) {
        viewModelScope.launch { readerPrefs.setReadingDirection(dir) }
    }

    fun setPageLayout(layout: PageLayout) {
        viewModelScope.launch { readerPrefs.setPageLayout(layout) }
    }

    fun setKeepScreenOn(on: Boolean) {
        viewModelScope.launch { readerPrefs.setKeepScreenOn(on) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            context.appPrefsDataStore.edit { it[AppPrefsKeys.THEME] = mode.name }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            context.appPrefsDataStore.edit { it[AppPrefsKeys.DYNAMIC_COLOR] = enabled }
        }
    }

    fun clearImageCache() {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _checkingUpdate.value = true
            val info = updateChecker.checkForUpdate()
            _updateInfo.value = info
            _checkingUpdate.value = false
        }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        viewModelScope.launch {
            context.appPrefsDataStore.edit { it[AppPrefsKeys.AUTO_UPDATE_CHECK] = enabled }
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                    .addTag("auto_update_check")
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    "auto_update_check",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            } else {
                workManager.cancelUniqueWork("auto_update_check")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val checkingUpdate by viewModel.checkingUpdate.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFitModeDialog by remember { mutableStateOf(false) }
    var showDirectionDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Reader section
            SectionHeader("Reader")

            ListItem(
                headlineContent = { Text("Fit Mode") },
                supportingContent = { Text(state.fitMode.name) },
                modifier = Modifier.clickable { showFitModeDialog = true },
            )
            ListItem(
                headlineContent = { Text("Reading Direction") },
                supportingContent = { Text(state.readingDirection.name) },
                modifier = Modifier.clickable { showDirectionDialog = true },
            )
            ListItem(
                headlineContent = { Text("Page Layout") },
                supportingContent = { Text(state.pageLayout.name) },
                modifier = Modifier.clickable { showLayoutDialog = true },
            )
            ListItem(
                headlineContent = { Text("Keep Screen On") },
                trailingContent = {
                    Switch(
                        checked = state.keepScreenOn,
                        onCheckedChange = { viewModel.setKeepScreenOn(it) },
                    )
                },
            )

            HorizontalDivider()

            // Appearance section
            SectionHeader("Appearance")

            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(state.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.clickable { showThemeDialog = true },
            )
            ListItem(
                headlineContent = { Text("Dynamic Color (Material You)") },
                trailingContent = {
                    Switch(
                        checked = state.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                    )
                },
            )

            HorizontalDivider()

            // Data section
            SectionHeader("Data")

            ListItem(
                headlineContent = { Text("Clear Image Cache") },
                supportingContent = { Text("Free up space by clearing cached images") },
                modifier = Modifier.clickable { showClearCacheDialog = true },
            )

            HorizontalDivider()

            // Updates section
            SectionHeader("Updates")

            ListItem(
                headlineContent = { Text("Check for Updates") },
                supportingContent = {
                    when {
                        checkingUpdate -> Text("Checking…")
                        updateInfo?.available == true ->
                            Text("Update available: v${updateInfo!!.latestVersion}")
                        updateInfo != null ->
                            Text("Up to date (v${updateInfo!!.currentVersion})")
                        else -> Text("Tap to check for a new version")
                    }
                },
                modifier = Modifier.clickable(enabled = !checkingUpdate) {
                    viewModel.checkForUpdates()
                },
            )

            ListItem(
                headlineContent = { Text("Auto-check for Updates") },
                supportingContent = { Text("Check daily in the background") },
                trailingContent = {
                    Switch(
                        checked = state.autoUpdateCheck,
                        onCheckedChange = { viewModel.setAutoUpdateCheck(it) },
                    )
                },
            )

            HorizontalDivider()

            // About section
            SectionHeader("About")

            val versionName = remember {
                try {
                    val ctx = viewModel.appContext
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.1.0"
                } catch (_: Exception) {
                    "0.1.0"
                }
            }
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(versionName) },
            )
        }
    }

    // Theme picker dialog
    if (showThemeDialog) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
            onSelect = { index ->
                viewModel.setThemeMode(ThemeMode.entries[index])
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    // Fit mode picker dialog
    if (showFitModeDialog) {
        ChoiceDialog(
            title = "Fit Mode",
            options = FitMode.entries.map { it.name },
            selectedIndex = FitMode.entries.indexOf(state.fitMode),
            onSelect = { index ->
                viewModel.setFitMode(FitMode.entries[index])
                showFitModeDialog = false
            },
            onDismiss = { showFitModeDialog = false },
        )
    }

    // Reading direction picker dialog
    if (showDirectionDialog) {
        ChoiceDialog(
            title = "Reading Direction",
            options = ReadingDirection.entries.map { it.name },
            selectedIndex = ReadingDirection.entries.indexOf(state.readingDirection),
            onSelect = { index ->
                viewModel.setReadingDirection(ReadingDirection.entries[index])
                showDirectionDialog = false
            },
            onDismiss = { showDirectionDialog = false },
        )
    }

    // Page layout picker dialog
    if (showLayoutDialog) {
        ChoiceDialog(
            title = "Page Layout",
            options = PageLayout.entries.map { it.name },
            selectedIndex = PageLayout.entries.indexOf(state.pageLayout),
            onSelect = { index ->
                viewModel.setPageLayout(PageLayout.entries[index])
                showLayoutDialog = false
            },
            onDismiss = { showLayoutDialog = false },
        )
    }

    // Clear cache confirmation
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Image Cache") },
            text = { Text("This will remove all cached images. They will be re-downloaded when needed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearImageCache()
                    showClearCacheDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    ListItem(
                        headlineContent = { Text(option) },
                        leadingContent = {
                            androidx.compose.material3.RadioButton(
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) },
                            )
                        },
                        modifier = Modifier.clickable { onSelect(index) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
