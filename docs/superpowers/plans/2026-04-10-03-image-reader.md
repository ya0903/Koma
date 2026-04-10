# Plan 3: Image Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a page-by-page image reader for CBZ/CBR/PDF content from Komga, with fit modes, reading direction support, tap-zone navigation, a page slider, and read progress sync. After this plan, users can actually *read* manga and comics through Koma.

**Architecture:** The reader is a full-screen Compose activity/screen with an overlay toolbar that auto-hides. Page images are loaded via Coil from authenticated Komga URLs (`/api/v1/books/{id}/pages/{n}`). A horizontal/vertical pager (Compose `HorizontalPager`/`VerticalPager`) drives navigation. Read progress is written to Room optimistically and synced to Komga via the existing `MediaServer.updateProgress()`. Reader settings (fit mode, direction) are stored in DataStore preferences.

**Tech Stack (new in this plan):** Compose Foundation Pager (built into Compose BOM), DataStore Preferences, Kotlin Coroutines (delay for debounce).

---

## File Structure

**New**
- `app/src/main/java/com/koma/client/ui/reader/image/ImageReaderScreen.kt` — full reader screen + ViewModel
- `app/src/main/java/com/koma/client/ui/reader/image/ReaderPage.kt` — single page composable with zoomable image
- `app/src/main/java/com/koma/client/ui/reader/image/ReaderOverlay.kt` — top bar + bottom slider overlay
- `app/src/main/java/com/koma/client/ui/reader/image/ReaderSettings.kt` — fit mode, direction, page layout bottom sheet
- `app/src/main/java/com/koma/client/ui/reader/common/ReaderPreferences.kt` — DataStore for reader defaults
- `app/src/main/java/com/koma/client/ui/reader/common/ReadingDirection.kt` — enum
- `app/src/main/java/com/koma/client/ui/reader/common/FitMode.kt` — enum
- `app/src/main/java/com/koma/client/ui/reader/common/PageLayout.kt` — enum
- `app/src/main/java/com/koma/client/data/db/entity/ReadProgressEntity.kt` — Room entity
- `app/src/main/java/com/koma/client/data/db/dao/ReadProgressDao.kt`
- `app/src/main/java/com/koma/client/domain/repo/ReadProgressRepository.kt`
- `app/src/main/java/com/koma/client/data/repo/ReadProgressRepositoryImpl.kt`

**Modified**
- `gradle/libs.versions.toml` — add DataStore
- `app/build.gradle.kts` — add DataStore dep
- `app/src/main/java/com/koma/client/data/db/KomaDatabase.kt` — add ReadProgressEntity + DAO
- `app/src/main/java/com/koma/client/di/DatabaseModule.kt` — provide ReadProgressDao
- `app/src/main/java/com/koma/client/di/RepositoryModule.kt` — bind ReadProgressRepository
- `app/src/main/java/com/koma/client/ui/nav/Routes.kt` — add READER route
- `app/src/main/java/com/koma/client/ui/nav/KomaNavHost.kt` — add reader destination
- `app/src/main/res/values/strings.xml` — reader strings

**Tests**
- `app/src/test/java/com/koma/client/ui/reader/common/ReaderEnumsTest.kt`
- `app/src/test/java/com/koma/client/data/db/ReadProgressDaoTest.kt`
- `app/src/test/java/com/koma/client/ui/reader/image/ImageReaderViewModelTest.kt`

---

### Task 1: Add DataStore dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add to version catalog**

In `[versions]`:
```toml
datastore = "1.1.1"
```

In `[libraries]`:
```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: Add to app dependencies**

```kotlin
implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: Verify build**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add DataStore preferences dependency"
```

---

### Task 2: Reader enums — FitMode, ReadingDirection, PageLayout

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/reader/common/FitMode.kt`
- Create: `app/src/main/java/com/koma/client/ui/reader/common/ReadingDirection.kt`
- Create: `app/src/main/java/com/koma/client/ui/reader/common/PageLayout.kt`
- Test: `app/src/test/java/com/koma/client/ui/reader/common/ReaderEnumsTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.koma.client.ui.reader.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderEnumsTest {

    @Test
    fun fitMode_has_all_variants() {
        assertThat(FitMode.entries.map { it.name }.toSet())
            .containsExactly("WIDTH", "HEIGHT", "SCREEN", "ORIGINAL")
    }

    @Test
    fun readingDirection_has_all_variants() {
        assertThat(ReadingDirection.entries.map { it.name }.toSet())
            .containsExactly("LTR", "RTL", "VERTICAL")
    }

    @Test
    fun pageLayout_has_all_variants() {
        assertThat(PageLayout.entries.map { it.name }.toSet())
            .containsExactly("SINGLE", "DOUBLE")
    }

    @Test
    fun readingDirection_isHorizontal() {
        assertThat(ReadingDirection.LTR.isHorizontal).isTrue()
        assertThat(ReadingDirection.RTL.isHorizontal).isTrue()
        assertThat(ReadingDirection.VERTICAL.isHorizontal).isFalse()
    }

    @Test
    fun readingDirection_isReversed() {
        assertThat(ReadingDirection.RTL.isReversed).isTrue()
        assertThat(ReadingDirection.LTR.isReversed).isFalse()
        assertThat(ReadingDirection.VERTICAL.isReversed).isFalse()
    }
}
```

- [ ] **Step 2: Run test to verify compile failure**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.ui.reader.common.ReaderEnumsTest"
```

- [ ] **Step 3: Create enums**

`FitMode.kt`:
```kotlin
package com.koma.client.ui.reader.common

enum class FitMode {
    WIDTH, HEIGHT, SCREEN, ORIGINAL
}
```

`ReadingDirection.kt`:
```kotlin
package com.koma.client.ui.reader.common

enum class ReadingDirection {
    LTR, RTL, VERTICAL;

    val isHorizontal: Boolean get() = this != VERTICAL
    val isReversed: Boolean get() = this == RTL
}
```

`PageLayout.kt`:
```kotlin
package com.koma.client.ui.reader.common

enum class PageLayout {
    SINGLE, DOUBLE
}
```

- [ ] **Step 4: Run test to verify PASS**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.ui.reader.common.ReaderEnumsTest"
```
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/reader/common/ app/src/test/java/com/koma/client/ui/reader/common/
git commit -m "feat: add reader enums — FitMode, ReadingDirection, PageLayout"
```

---

### Task 3: ReaderPreferences (DataStore)

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/reader/common/ReaderPreferences.kt`

- [ ] **Step 1: Create ReaderPreferences**

```kotlin
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

@Singleton
class ReaderPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fitModeKey = stringPreferencesKey("fit_mode")
    private val directionKey = stringPreferencesKey("reading_direction")
    private val layoutKey = stringPreferencesKey("page_layout")
    private val keepScreenOnKey = stringPreferencesKey("keep_screen_on")

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
}
```

- [ ] **Step 2: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/reader/common/ReaderPreferences.kt
git commit -m "feat: add ReaderPreferences backed by DataStore"
```

---

### Task 4: ReadProgress Room entity + DAO

**Files:**
- Create: `app/src/main/java/com/koma/client/data/db/entity/ReadProgressEntity.kt`
- Create: `app/src/main/java/com/koma/client/data/db/dao/ReadProgressDao.kt`
- Modify: `app/src/main/java/com/koma/client/data/db/KomaDatabase.kt`
- Modify: `app/src/main/java/com/koma/client/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/koma/client/data/db/ReadProgressDaoTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify compile failure**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.data.db.ReadProgressDaoTest"
```

- [ ] **Step 3: Create `ReadProgressEntity.kt`**

```kotlin
package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_progress")
data class ReadProgressEntity(
    @PrimaryKey val bookId: String,
    val page: Int,
    val completed: Boolean,
    val locator: String?,
    val updatedAtEpochMs: Long,
    val dirty: Boolean,
)
```

- [ ] **Step 4: Create `ReadProgressDao.kt`**

```kotlin
package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.koma.client.data.db.entity.ReadProgressEntity

@Dao
interface ReadProgressDao {

    @Query("SELECT * FROM read_progress WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): ReadProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadProgressEntity)

    @Query("SELECT * FROM read_progress WHERE dirty = 1")
    suspend fun getDirty(): List<ReadProgressEntity>

    @Query("UPDATE read_progress SET dirty = 0 WHERE bookId = :bookId")
    suspend fun markClean(bookId: String)
}
```

- [ ] **Step 5: Update KomaDatabase**

Add `ReadProgressEntity` to the entities array and bump version to 3 (still using destructive migration since there's no real user data in dev). Add `abstract fun readProgressDao(): ReadProgressDao`.

- [ ] **Step 6: Update DatabaseModule**

Add:
```kotlin
@Provides fun provideReadProgressDao(db: KomaDatabase): ReadProgressDao = db.readProgressDao()
```

- [ ] **Step 7: Run test to verify PASS**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.data.db.ReadProgressDaoTest"
```
Expected: PASS (4 tests).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/koma/client/data/db/ app/src/test/java/com/koma/client/data/db/ReadProgressDaoTest.kt app/src/main/java/com/koma/client/di/DatabaseModule.kt
git commit -m "feat: add ReadProgress Room entity and DAO"
```

---

### Task 5: ReadProgressRepository

**Files:**
- Create: `app/src/main/java/com/koma/client/domain/repo/ReadProgressRepository.kt`
- Create: `app/src/main/java/com/koma/client/data/repo/ReadProgressRepositoryImpl.kt`
- Modify: `app/src/main/java/com/koma/client/di/RepositoryModule.kt`

- [ ] **Step 1: Create interface**

```kotlin
package com.koma.client.domain.repo

import com.koma.client.domain.model.ReadProgress

interface ReadProgressRepository {
    suspend fun get(bookId: String): ReadProgress?
    suspend fun save(bookId: String, page: Int, completed: Boolean)
    suspend fun syncDirty(syncFn: suspend (ReadProgress) -> Unit)
}
```

- [ ] **Step 2: Create implementation**

```kotlin
package com.koma.client.data.repo

import com.koma.client.data.db.dao.ReadProgressDao
import com.koma.client.data.db.entity.ReadProgressEntity
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.repo.ReadProgressRepository
import javax.inject.Inject

class ReadProgressRepositoryImpl @Inject constructor(
    private val dao: ReadProgressDao,
) : ReadProgressRepository {

    override suspend fun get(bookId: String): ReadProgress? {
        return dao.getByBookId(bookId)?.let {
            ReadProgress(
                bookId = it.bookId,
                page = it.page,
                completed = it.completed,
                locator = it.locator,
                updatedAtEpochMs = it.updatedAtEpochMs,
            )
        }
    }

    override suspend fun save(bookId: String, page: Int, completed: Boolean) {
        dao.upsert(
            ReadProgressEntity(
                bookId = bookId,
                page = page,
                completed = completed,
                locator = null,
                updatedAtEpochMs = System.currentTimeMillis(),
                dirty = true,
            )
        )
    }

    override suspend fun syncDirty(syncFn: suspend (ReadProgress) -> Unit) {
        val dirty = dao.getDirty()
        for (entity in dirty) {
            val progress = ReadProgress(
                bookId = entity.bookId,
                page = entity.page,
                completed = entity.completed,
                locator = entity.locator,
                updatedAtEpochMs = entity.updatedAtEpochMs,
            )
            try {
                syncFn(progress)
                dao.markClean(entity.bookId)
            } catch (_: Exception) {
                // Will retry next sync cycle
            }
        }
    }
}
```

- [ ] **Step 3: Bind in RepositoryModule**

Add to `RepositoryModule`:
```kotlin
@Binds @Singleton
abstract fun bindReadProgressRepository(impl: ReadProgressRepositoryImpl): ReadProgressRepository
```

- [ ] **Step 4: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koma/client/domain/repo/ReadProgressRepository.kt app/src/main/java/com/koma/client/data/repo/ReadProgressRepositoryImpl.kt app/src/main/java/com/koma/client/di/RepositoryModule.kt
git commit -m "feat: add ReadProgressRepository with dirty-sync support"
```

---

### Task 6: ReaderPage composable — zoomable page image

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/reader/image/ReaderPage.kt`

- [ ] **Step 1: Create ReaderPage**

A single page composable that loads an image URL via Coil and supports pinch-to-zoom + pan using Compose `graphicsLayer` + `transformable` modifier.

```kotlin
package com.koma.client.ui.reader.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import com.koma.client.ui.reader.common.FitMode

@Composable
fun ReaderPage(
    imageUrl: String,
    fitMode: FitMode,
    onTap: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += panChange
    }

    val contentScale = when (fitMode) {
        FitMode.WIDTH -> ContentScale.FillWidth
        FitMode.HEIGHT -> ContentScale.FillHeight
        FitMode.SCREEN -> ContentScale.Fit
        FitMode.ORIGINAL -> ContentScale.None
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap(it) },
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .transformable(state = transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
        )
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/reader/image/ReaderPage.kt
git commit -m "feat: add ReaderPage composable with zoom and pan"
```

---

### Task 7: ReaderOverlay — top bar + page slider

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/reader/image/ReaderOverlay.kt`

- [ ] **Step 1: Create ReaderOverlay**

```kotlin
package com.koma.client.ui.reader.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ReaderOverlay(
    visible: Boolean,
    title: String,
    currentPage: Int,
    totalPages: Int,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Top bar
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, "Settings", tint = Color.White)
                }
            }
        }

        // Bottom slider
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${currentPage + 1}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text("$totalPages", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                if (totalPages > 1) {
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { onPageChange(it.toInt()) },
                        valueRange = 0f..(totalPages - 1).toFloat(),
                        steps = (totalPages - 2).coerceAtLeast(0),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/reader/image/ReaderOverlay.kt
git commit -m "feat: add ReaderOverlay with top bar and page slider"
```

---

### Task 8: ReaderSettings bottom sheet

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/reader/image/ReaderSettings.kt`

- [ ] **Step 1: Create ReaderSettings**

```kotlin
package com.koma.client.ui.reader.image

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koma.client.ui.reader.common.FitMode
import com.koma.client.ui.reader.common.PageLayout
import com.koma.client.ui.reader.common.ReadingDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    fitMode: FitMode,
    readingDirection: ReadingDirection,
    pageLayout: PageLayout,
    keepScreenOn: Boolean,
    onFitModeChange: (FitMode) -> Unit,
    onDirectionChange: (ReadingDirection) -> Unit,
    onLayoutChange: (PageLayout) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Reader Settings", style = MaterialTheme.typography.titleMedium)

            // Fit mode
            Text("Fit Mode", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = fitMode == mode,
                        onClick = { onFitModeChange(mode) },
                        label = { Text(mode.name) },
                    )
                }
            }

            // Reading direction
            Text("Reading Direction", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = readingDirection == dir,
                        onClick = { onDirectionChange(dir) },
                        label = { Text(dir.name) },
                    )
                }
            }

            // Page layout
            Text("Page Layout", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageLayout.entries.forEach { layout ->
                    FilterChip(
                        selected = pageLayout == layout,
                        onClick = { onLayoutChange(layout) },
                        label = { Text(layout.name) },
                    )
                }
            }

            // Keep screen on
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Keep Screen On")
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/reader/image/ReaderSettings.kt
git commit -m "feat: add ReaderSettings bottom sheet"
```

---

### Task 9: ImageReaderScreen + ViewModel (the main reader)

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/reader/image/ImageReaderScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/koma/client/ui/reader/image/ImageReaderViewModelTest.kt`

- [ ] **Step 1: Add string resources**

```xml
<string name="reader_page_format">Page %1$d of %2$d</string>
```

- [ ] **Step 2: Write failing ViewModel test**

```kotlin
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
```

- [ ] **Step 3: Create `ImageReaderScreen.kt`**

Contains `ImageReaderUiState`, `ImageReaderViewModel`, and `ImageReaderScreen` composable.

```kotlin
package com.koma.client.ui.reader.image

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.repo.ReadProgressRepository
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.ui.reader.common.FitMode
import com.koma.client.ui.reader.common.PageLayout
import com.koma.client.ui.reader.common.ReaderPreferences
import com.koma.client.ui.reader.common.ReadingDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageReaderUiState(
    val loading: Boolean = true,
    val bookTitle: String = "",
    val pageUrls: List<String> = emptyList(),
    val currentPage: Int = 0,
    val overlayVisible: Boolean = false,
    val fitMode: FitMode = FitMode.WIDTH,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val pageLayout: PageLayout = PageLayout.SINGLE,
    val keepScreenOn: Boolean = true,
    val showSettings: Boolean = false,
    val error: String? = null,
)

/**
 * Non-Hilt constructor for unit testing — takes pre-resolved data.
 * The @HiltViewModel version below delegates to this after resolving the book.
 */
class ImageReaderViewModel(
    private val bookId: String,
    pageUrls: List<String>,
    bookTitle: String,
    private val progressRepo: ReadProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ImageReaderUiState(
            loading = false,
            bookTitle = bookTitle,
            pageUrls = pageUrls,
        )
    )
    val uiState: StateFlow<ImageReaderUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (_uiState.value.pageUrls.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentPage = clamped) }
        debounceSaveProgress(clamped)
    }

    fun toggleOverlay() {
        _uiState.update { it.copy(overlayVisible = !it.overlayVisible) }
    }

    fun setFitMode(mode: FitMode) {
        _uiState.update { it.copy(fitMode = mode) }
    }

    fun setReadingDirection(dir: ReadingDirection) {
        _uiState.update { it.copy(readingDirection = dir) }
    }

    fun setPageLayout(layout: PageLayout) {
        _uiState.update { it.copy(pageLayout = layout) }
    }

    fun setKeepScreenOn(on: Boolean) {
        _uiState.update { it.copy(keepScreenOn = on) }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    private fun debounceSaveProgress(page: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val total = _uiState.value.pageUrls.size
            val completed = page >= total - 1
            progressRepo.save(bookId, page, completed)
        }
    }
}

/**
 * Hilt-injected ViewModel that resolves the book from SavedStateHandle,
 * fetches page URLs from the MediaServer, and delegates to the core ViewModel logic.
 */
@HiltViewModel
class HiltImageReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
    private val progressRepo: ReadProgressRepository,
    private val readerPrefs: ReaderPreferences,
) : ViewModel() {

    private val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _uiState = MutableStateFlow(ImageReaderUiState())
    val uiState: StateFlow<ImageReaderUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        loadBook()
        observePreferences()
    }

    private fun loadBook() {
        viewModelScope.launch {
            try {
                val server = serverRepo.getActive() ?: return@launch
                val entity = com.koma.client.data.db.entity.ServerEntity(
                    id = server.id, type = server.type, name = server.name,
                    baseUrl = server.baseUrl, username = "", encPassword = "",
                    encJwtToken = null, encSessionCookie = null,
                    isActive = server.isActive, lastSyncAtEpochMs = 0L,
                )
                val mediaServer = registry.get(entity)
                val book = mediaServer.book(bookId)
                val urls = (0 until book.pageCount).map { page ->
                    mediaServer.pageUrl(bookId, page)
                }
                val savedProgress = progressRepo.get(bookId)
                _uiState.update {
                    it.copy(
                        loading = false,
                        bookTitle = book.title,
                        pageUrls = urls,
                        currentPage = savedProgress?.page ?: 0,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            readerPrefs.fitMode.collect { mode -> _uiState.update { it.copy(fitMode = mode) } }
        }
        viewModelScope.launch {
            readerPrefs.readingDirection.collect { dir -> _uiState.update { it.copy(readingDirection = dir) } }
        }
        viewModelScope.launch {
            readerPrefs.pageLayout.collect { layout -> _uiState.update { it.copy(pageLayout = layout) } }
        }
        viewModelScope.launch {
            readerPrefs.keepScreenOn.collect { on -> _uiState.update { it.copy(keepScreenOn = on) } }
        }
    }

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (_uiState.value.pageUrls.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentPage = clamped) }
        debounceSaveProgress(clamped)
    }

    fun toggleOverlay() = _uiState.update { it.copy(overlayVisible = !it.overlayVisible) }
    fun showSettings() = _uiState.update { it.copy(showSettings = true) }
    fun hideSettings() = _uiState.update { it.copy(showSettings = false) }

    fun setFitMode(mode: FitMode) {
        _uiState.update { it.copy(fitMode = mode) }
        viewModelScope.launch { readerPrefs.setFitMode(mode) }
    }

    fun setReadingDirection(dir: ReadingDirection) {
        _uiState.update { it.copy(readingDirection = dir) }
        viewModelScope.launch { readerPrefs.setReadingDirection(dir) }
    }

    fun setPageLayout(layout: PageLayout) {
        _uiState.update { it.copy(pageLayout = layout) }
        viewModelScope.launch { readerPrefs.setPageLayout(layout) }
    }

    fun setKeepScreenOn(on: Boolean) {
        _uiState.update { it.copy(keepScreenOn = on) }
        viewModelScope.launch { readerPrefs.setKeepScreenOn(on) }
    }

    private fun debounceSaveProgress(page: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val total = _uiState.value.pageUrls.size
            progressRepo.save(bookId, page, page >= total - 1)
        }
    }
}

@Composable
fun ImageReaderScreen(
    onBack: () -> Unit,
    viewModel: HiltImageReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Keep screen on
    val view = LocalView.current
    LaunchedEffect(state.keepScreenOn) {
        if (state.keepScreenOn) {
            view.keepScreenOn = true
        } else {
            view.keepScreenOn = false
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    state.error?.let { err ->
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(err, color = Color.White)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val direction = state.readingDirection

        if (direction.isHorizontal) {
            val pagerState = rememberPagerState(
                initialPage = state.currentPage,
                pageCount = { state.pageUrls.size },
            )

            LaunchedEffect(pagerState.currentPage) {
                viewModel.goToPage(pagerState.currentPage)
            }

            LaunchedEffect(state.currentPage) {
                if (pagerState.currentPage != state.currentPage) {
                    pagerState.animateScrollToPage(state.currentPage)
                }
            }

            HorizontalPager(
                state = pagerState,
                reverseLayout = direction.isReversed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ReaderPage(
                    imageUrl = state.pageUrls[page],
                    fitMode = state.fitMode,
                    onTap = { viewModel.toggleOverlay() },
                )
            }
        } else {
            val pagerState = rememberPagerState(
                initialPage = state.currentPage,
                pageCount = { state.pageUrls.size },
            )

            LaunchedEffect(pagerState.currentPage) {
                viewModel.goToPage(pagerState.currentPage)
            }

            LaunchedEffect(state.currentPage) {
                if (pagerState.currentPage != state.currentPage) {
                    pagerState.animateScrollToPage(state.currentPage)
                }
            }

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ReaderPage(
                    imageUrl = state.pageUrls[page],
                    fitMode = state.fitMode,
                    onTap = { viewModel.toggleOverlay() },
                )
            }
        }

        // Overlay
        ReaderOverlay(
            visible = state.overlayVisible,
            title = state.bookTitle,
            currentPage = state.currentPage,
            totalPages = state.pageUrls.size,
            onBack = onBack,
            onPageChange = { viewModel.goToPage(it) },
            onSettingsClick = { viewModel.showSettings() },
        )

        // Settings bottom sheet
        if (state.showSettings) {
            ReaderSettingsSheet(
                fitMode = state.fitMode,
                readingDirection = state.readingDirection,
                pageLayout = state.pageLayout,
                keepScreenOn = state.keepScreenOn,
                onFitModeChange = viewModel::setFitMode,
                onDirectionChange = viewModel::setReadingDirection,
                onLayoutChange = viewModel::setPageLayout,
                onKeepScreenOnChange = viewModel::setKeepScreenOn,
                onDismiss = { viewModel.hideSettings() },
            )
        }
    }
}
```

- [ ] **Step 4: Run ViewModel test**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.ui.reader.image.ImageReaderViewModelTest"
```
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/reader/image/ImageReaderScreen.kt app/src/test/java/com/koma/client/ui/reader/image/ app/src/main/res/values/strings.xml
git commit -m "feat: add ImageReaderScreen with pager, overlay, settings, and progress sync"
```

---

### Task 10: Wire reader into navigation

**Files:**
- Modify: `app/src/main/java/com/koma/client/ui/nav/Routes.kt`
- Modify: `app/src/main/java/com/koma/client/ui/nav/KomaNavHost.kt`

- [ ] **Step 1: Add reader route**

In `Routes.kt`, add:
```kotlin
const val IMAGE_READER = "image_reader/{bookId}"
fun imageReader(bookId: String) = "image_reader/$bookId"
```

- [ ] **Step 2: Update KomaNavHost**

Replace the `BOOK_DETAIL` placeholder composable with navigation to the reader. When a user taps a book in the series detail, navigate to the image reader:

Update the `BOOK_DETAIL` composable to redirect to the reader, or replace `BOOK_DETAIL` with `IMAGE_READER`:

```kotlin
composable(Routes.IMAGE_READER) {
    ImageReaderScreen(onBack = { navController.popBackStack() })
}
```

Update `SeriesDetailScreen`'s `onBookClick` in KomaNavHost to use `Routes.imageReader(bookId)` instead of `Routes.bookDetail(bookId)`.

- [ ] **Step 3: Verify full build + tests**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/nav/
git commit -m "feat: wire ImageReaderScreen into navigation"
```

---

### Task 11: Full verification + tag

**Files:** none (verification only)

- [ ] **Step 1: Run full test suite**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest
```
Expected: All tests pass (~47 total: 33 from Plan 2 + ~14 new).

- [ ] **Step 2: Clean build**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew clean :app:assembleDebug
```

- [ ] **Step 3: Tag milestone**

```bash
git tag -a plan-03-image-reader -m "Plan 3 complete: image reader with pager, zoom, fit modes, direction, progress sync"
```

- [ ] **Step 4: Verify clean working tree**

```bash
git status
```

---

## Plan 3 Completion Criteria

1. All tests pass (Plan 1 + Plan 2 + Plan 3 tests)
2. Debug APK builds
3. User can tap a book from series detail → full-screen image reader opens
4. Pages swipe horizontally (LTR/RTL) or vertically depending on setting
5. Tap to show/hide overlay (top bar with title + back, bottom with page slider)
6. Settings sheet: fit mode (width/height/screen/original), reading direction, page layout, keep screen on
7. Pinch-to-zoom on pages with double-tap reset
8. Read progress saved to Room and synced to Komga (debounced)
9. Page slider allows jumping to any page
10. Git tag `plan-03-image-reader` marks the milestone

## What Plan 3 Deliberately Leaves Out

- Double-page layout rendering (the enum exists but only SINGLE is functionally implemented — DOUBLE requires pairing logic, deferred to Plan 5)
- Custom tap zones (Plan 5)
- Per-book zoom memory (Plan 5)
- Crop whitespace (Plan 5)
- Background color picker (Plan 5)
- Volume-key page turn (Plan 5)
- Brightness slider (Plan 5)
- EPUB reader (Plan 4)
- Bookmarks (Plan 5)
