package com.koma.client.ui.reader.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_prefs")

/** Theme options for EPUB reader */
enum class EpubTheme(val label: String, val bgColor: String, val textColor: String) {
    LIGHT("Light", "#FFFFFF", "#1C1B1F"),
    SEPIA("Sepia", "#F5E6C8", "#3D2B1F"),
    DARK("Dark", "#1C1B1F", "#E6E1E5"),
    KOMA_NAVY("Koma Navy", "#0D1B2A", "#B0C4DE"),
}

/** Font family options for EPUB reader */
enum class EpubFontFamily(val label: String, val cssValue: String) {
    SYSTEM("System Default", "system-ui, sans-serif"),
    SERIF("Serif", "Georgia, 'Times New Roman', serif"),
    SANS_SERIF("Sans Serif", "'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"),
    MONOSPACE("Monospace", "'Courier New', Courier, monospace"),
}

@Singleton
class ReaderPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Image reader keys
    private val fitModeKey = stringPreferencesKey("fit_mode")
    private val directionKey = stringPreferencesKey("reading_direction")
    private val layoutKey = stringPreferencesKey("page_layout")
    private val keepScreenOnKey = stringPreferencesKey("keep_screen_on")

    // EPUB reader keys
    private val epubFontFamilyKey = stringPreferencesKey("epub_font_family")
    private val epubFontSizeKey = stringPreferencesKey("epub_font_size")
    private val epubLineHeightKey = stringPreferencesKey("epub_line_height")
    private val epubMarginKey = stringPreferencesKey("epub_margin")
    private val epubThemeKey = stringPreferencesKey("epub_theme")
    private val epubJustifiedKey = stringPreferencesKey("epub_justified")
    private val epubOverridePublisherFontKey = stringPreferencesKey("epub_override_publisher_font")

    // --- Image reader preferences ---

    val fitMode: Flow<FitMode> = context.readerDataStore.data.map { prefs ->
        prefs[fitModeKey]?.let { FitMode.valueOf(it) } ?: FitMode.WIDTH
    }

    val readingDirection: Flow<ReadingDirection> = context.readerDataStore.data.map { prefs ->
        prefs[directionKey]?.let { ReadingDirection.valueOf(it) } ?: ReadingDirection.LTR
    }

    val pageLayout: Flow<PageLayout> = context.readerDataStore.data.map { prefs ->
        prefs[layoutKey]?.let { PageLayout.valueOf(it) } ?: PageLayout.SINGLE
    }

    val keepScreenOn: Flow<Boolean> = context.readerDataStore.data.map { prefs ->
        prefs[keepScreenOnKey]?.toBooleanStrictOrNull() ?: true
    }

    suspend fun setFitMode(mode: FitMode) {
        context.readerDataStore.edit { it[fitModeKey] = mode.name }
    }

    suspend fun setReadingDirection(dir: ReadingDirection) {
        context.readerDataStore.edit { it[directionKey] = dir.name }
    }

    suspend fun setPageLayout(layout: PageLayout) {
        context.readerDataStore.edit { it[layoutKey] = layout.name }
    }

    suspend fun setKeepScreenOn(on: Boolean) {
        context.readerDataStore.edit { it[keepScreenOnKey] = on.toString() }
    }

    // --- EPUB reader preferences ---

    val epubFontFamily: Flow<EpubFontFamily> = context.readerDataStore.data.map { prefs ->
        prefs[epubFontFamilyKey]?.let { runCatching { EpubFontFamily.valueOf(it) }.getOrNull() }
            ?: EpubFontFamily.SYSTEM
    }

    val epubFontSize: Flow<Int> = context.readerDataStore.data.map { prefs ->
        prefs[epubFontSizeKey]?.toIntOrNull() ?: 18
    }

    val epubLineHeight: Flow<Float> = context.readerDataStore.data.map { prefs ->
        prefs[epubLineHeightKey]?.toFloatOrNull() ?: 1.6f
    }

    val epubMargin: Flow<Int> = context.readerDataStore.data.map { prefs ->
        prefs[epubMarginKey]?.toIntOrNull() ?: 16
    }

    val epubTheme: Flow<EpubTheme> = context.readerDataStore.data.map { prefs ->
        prefs[epubThemeKey]?.let { runCatching { EpubTheme.valueOf(it) }.getOrNull() }
            ?: EpubTheme.LIGHT
    }

    val epubJustified: Flow<Boolean> = context.readerDataStore.data.map { prefs ->
        prefs[epubJustifiedKey]?.toBooleanStrictOrNull() ?: true
    }

    val epubOverridePublisherFont: Flow<Boolean> = context.readerDataStore.data.map { prefs ->
        prefs[epubOverridePublisherFontKey]?.toBooleanStrictOrNull() ?: false
    }

    suspend fun setEpubFontFamily(family: EpubFontFamily) {
        context.readerDataStore.edit { it[epubFontFamilyKey] = family.name }
    }

    suspend fun setEpubFontSize(size: Int) {
        context.readerDataStore.edit { it[epubFontSizeKey] = size.toString() }
    }

    suspend fun setEpubLineHeight(height: Float) {
        context.readerDataStore.edit { it[epubLineHeightKey] = height.toString() }
    }

    suspend fun setEpubMargin(margin: Int) {
        context.readerDataStore.edit { it[epubMarginKey] = margin.toString() }
    }

    suspend fun setEpubTheme(theme: EpubTheme) {
        context.readerDataStore.edit { it[epubThemeKey] = theme.name }
    }

    suspend fun setEpubJustified(justified: Boolean) {
        context.readerDataStore.edit { it[epubJustifiedKey] = justified.toString() }
    }

    suspend fun setEpubOverridePublisherFont(override: Boolean) {
        context.readerDataStore.edit { it[epubOverridePublisherFontKey] = override.toString() }
    }
}
