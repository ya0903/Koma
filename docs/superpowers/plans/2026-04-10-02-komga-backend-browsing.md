# Plan 2: Komga Backend + Browsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plug in the first concrete `MediaServer` implementation (Komga) so users can add a Komga server, authenticate, and browse Libraries → Series → Series Detail → Book List with real data and thumbnails end-to-end.

**Architecture:** `KomgaMediaServer` implements `MediaServer` and is backed by a `KomgaApi` Retrofit service. OkHttp handles Basic auth. Coil 3 shares the authenticated OkHttp client for thumbnail loading. Room gains cache tables (`cached_libraries`, `cached_series`, `cached_books`) written by repos that combine network fetches with local cache. Credential storage moves from plaintext to `EncryptedSharedPreferences`. UI adds the server-add flow, library grid, series grid (with filters), and series detail screen.

**Tech Stack (new in this plan):** Retrofit 2.11 + OkHttp 4.12 + Kotlinx Serialization converter, Coil 3 (Compose), MockWebServer (tests), EncryptedSharedPreferences (AndroidX Security), Paging 3 (for series listing).

---

## File Structure (new and modified files)

**New — Networking & Komga data layer**
- `app/src/main/java/com/koma/client/data/server/komga/dto/` — 8 DTO files
- `app/src/main/java/com/koma/client/data/server/komga/KomgaApi.kt` — Retrofit service
- `app/src/main/java/com/koma/client/data/server/komga/KomgaMediaServer.kt` — MediaServer impl
- `app/src/main/java/com/koma/client/data/server/komga/KomgaMapper.kt` — DTO → domain mappers
- `app/src/main/java/com/koma/client/data/auth/CredentialStore.kt` — EncryptedSharedPreferences wrapper
- `app/src/main/java/com/koma/client/data/server/MediaServerRegistry.kt` — resolves server ID → MediaServer

**New — Room cache layer**
- `app/src/main/java/com/koma/client/data/db/entity/CachedLibraryEntity.kt`
- `app/src/main/java/com/koma/client/data/db/entity/CachedSeriesEntity.kt`
- `app/src/main/java/com/koma/client/data/db/entity/CachedBookEntity.kt`
- `app/src/main/java/com/koma/client/data/db/dao/LibraryDao.kt`
- `app/src/main/java/com/koma/client/data/db/dao/SeriesDao.kt`
- `app/src/main/java/com/koma/client/data/db/dao/BookDao.kt`

**New — Repositories**
- `app/src/main/java/com/koma/client/domain/repo/LibraryRepository.kt`
- `app/src/main/java/com/koma/client/domain/repo/SeriesRepository.kt`
- `app/src/main/java/com/koma/client/domain/repo/BookRepository.kt`
- `app/src/main/java/com/koma/client/data/repo/LibraryRepositoryImpl.kt`
- `app/src/main/java/com/koma/client/data/repo/SeriesRepositoryImpl.kt`
- `app/src/main/java/com/koma/client/data/repo/BookRepositoryImpl.kt`

**New — UI screens**
- `app/src/main/java/com/koma/client/ui/auth/AddServerScreen.kt` — type picker, URL, credentials, test
- `app/src/main/java/com/koma/client/ui/library/LibraryScreen.kt` — grid of libraries
- `app/src/main/java/com/koma/client/ui/series/SeriesListScreen.kt` — grid with filters/sort
- `app/src/main/java/com/koma/client/ui/series/SeriesDetailScreen.kt` — metadata + book list

**Modified**
- `gradle/libs.versions.toml` — add Retrofit, OkHttp, Coil, Paging, Security deps
- `app/build.gradle.kts` — add new deps
- `app/src/main/java/com/koma/client/data/db/KomaDatabase.kt` — add new entities + DAOs
- `app/src/main/java/com/koma/client/di/DatabaseModule.kt` — expose new DAOs
- `app/src/main/java/com/koma/client/di/RepositoryModule.kt` — bind new repos
- `app/src/main/java/com/koma/client/ui/nav/Routes.kt` — add new routes
- `app/src/main/java/com/koma/client/ui/nav/KomaNavHost.kt` — add new screen destinations
- `app/src/main/java/com/koma/client/ui/home/HomeScreen.kt` — display library grid when servers exist
- `app/src/main/java/com/koma/client/domain/repo/ServerRepository.kt` — add insert/delete/setActive methods
- `app/src/main/java/com/koma/client/data/repo/ServerRepositoryImpl.kt` — implement new methods
- `app/src/main/res/values/strings.xml` — add new string resources

**Tests**
- `app/src/test/java/com/koma/client/data/server/komga/KomgaMapperTest.kt`
- `app/src/test/java/com/koma/client/data/server/komga/KomgaApiTest.kt`
- `app/src/test/java/com/koma/client/data/server/komga/KomgaMediaServerTest.kt`
- `app/src/test/java/com/koma/client/data/db/LibraryDaoTest.kt`
- `app/src/test/java/com/koma/client/ui/auth/AddServerViewModelTest.kt`

---

### Task 1: Add networking dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions and libraries to catalog**

Add to `gradle/libs.versions.toml`:

```toml
# In [versions] section, add:
retrofit = "2.11.0"
okhttp = "4.12.0"
coil = "3.1.0"
paging = "3.3.2"
securityCrypto = "1.1.0-alpha06"
retrofitKotlinxSerialization = "2.1.0"

# In [libraries] section, add:
retrofit-core = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
okhttp-core = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofitKotlinxSerialization" }

coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }

androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

- [ ] **Step 2: Add dependencies to `app/build.gradle.kts`**

Add in the `dependencies` block:

```kotlin
implementation(libs.retrofit.core)
implementation(libs.okhttp.core)
implementation(libs.okhttp.logging)
implementation(libs.retrofit.kotlinx.serialization)

implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)

implementation(libs.androidx.paging.runtime)
implementation(libs.androidx.paging.compose)

implementation(libs.androidx.security.crypto)

testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 3: Verify build**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add Retrofit, OkHttp, Coil, Paging, Security deps"
```

---

### Task 2: Komga DTOs

**Files:**
- Create: `app/src/main/java/com/koma/client/data/server/komga/dto/KomgaLibraryDto.kt`
- Create: `app/src/main/java/com/koma/client/data/server/komga/dto/KomgaSeriesDto.kt`
- Create: `app/src/main/java/com/koma/client/data/server/komga/dto/KomgaBookDto.kt`
- Create: `app/src/main/java/com/koma/client/data/server/komga/dto/KomgaPageDto.kt`
- Create: `app/src/main/java/com/koma/client/data/server/komga/dto/KomgaReadProgressDto.kt`
- Create: `app/src/main/java/com/koma/client/data/server/komga/dto/KomgaUserDto.kt`
- Create: `app/src/main/java/com/koma/client/data/server/komga/dto/KomgaPageWrapper.kt`
- Create: `app/src/main/java/com/koma/client/data/server/komga/dto/KomgaMediaDto.kt`

DTOs use `@Serializable` (kotlinx.serialization). Only include fields Koma actually uses — skip admin-only fields.

- [ ] **Step 1: Create all DTO files**

`KomgaLibraryDto.kt`:
```kotlin
package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaLibraryDto(
    val id: String,
    val name: String,
    val unavailable: Boolean = false,
)
```

`KomgaSeriesDto.kt`:
```kotlin
package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaSeriesDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val booksCount: Int,
    val booksReadCount: Int = 0,
    val booksUnreadCount: Int = 0,
    val booksInProgressCount: Int = 0,
    val metadata: KomgaSeriesMetadataDto = KomgaSeriesMetadataDto(),
    val booksMetadata: KomgaBooksMetadataAggregationDto = KomgaBooksMetadataAggregationDto(),
    val deleted: Boolean = false,
    val oneshot: Boolean = false,
)

@Serializable
data class KomgaSeriesMetadataDto(
    val title: String = "",
    val titleSort: String = "",
    val summary: String = "",
    val status: String = "ONGOING",
    val readingDirection: String? = null,
    val publisher: String = "",
    val language: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class KomgaBooksMetadataAggregationDto(
    val summary: String = "",
    val authors: List<KomgaAuthorDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val releaseDate: String? = null,
)

@Serializable
data class KomgaAuthorDto(
    val name: String,
    val role: String,
)
```

`KomgaBookDto.kt`:
```kotlin
package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaBookDto(
    val id: String,
    val seriesId: String,
    val seriesTitle: String = "",
    val libraryId: String = "",
    val name: String,
    val number: Int = 0,
    val sizeBytes: Long = 0,
    val media: KomgaMediaDto = KomgaMediaDto(),
    val metadata: KomgaBookMetadataDto = KomgaBookMetadataDto(),
    val readProgress: KomgaReadProgressDto? = null,
    val deleted: Boolean = false,
    val oneshot: Boolean = false,
)

@Serializable
data class KomgaBookMetadataDto(
    val title: String = "",
    val number: String = "",
    val numberSort: Float = 0f,
    val summary: String = "",
    val authors: List<KomgaAuthorDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val isbn: String = "",
    val releaseDate: String? = null,
)
```

`KomgaMediaDto.kt`:
```kotlin
package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaMediaDto(
    val status: String = "READY",
    val mediaType: String = "",
    val pagesCount: Int = 0,
    val comment: String = "",
    val epubDivinaCompatible: Boolean = false,
)
```

`KomgaPageDto.kt`:
```kotlin
package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaPageDto(
    val number: Int,
    val fileName: String = "",
    val mediaType: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
)
```

`KomgaReadProgressDto.kt`:
```kotlin
package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaReadProgressDto(
    val page: Int = 0,
    val completed: Boolean = false,
    val readDate: String? = null,
    val created: String? = null,
    val lastModified: String? = null,
)

@Serializable
data class KomgaReadProgressUpdateDto(
    val page: Int? = null,
    val completed: Boolean? = null,
)
```

`KomgaUserDto.kt`:
```kotlin
package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaUserDto(
    val id: String,
    val email: String,
    val roles: List<String> = emptyList(),
)
```

`KomgaPageWrapper.kt`:
```kotlin
package com.koma.client.data.server.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class KomgaPageWrapper<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0,
    val size: Int = 20,
    val first: Boolean = true,
    val last: Boolean = true,
    val empty: Boolean = false,
    val numberOfElements: Int = 0,
)
```

- [ ] **Step 2: Verify build compiles**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/koma/client/data/server/komga/dto/
git commit -m "feat: add Komga API DTOs"
```

---

### Task 3: KomgaApi Retrofit service

**Files:**
- Create: `app/src/main/java/com/koma/client/data/server/komga/KomgaApi.kt`

- [ ] **Step 1: Create Retrofit service interface**

```kotlin
package com.koma.client.data.server.komga

import com.koma.client.data.server.komga.dto.KomgaBookDto
import com.koma.client.data.server.komga.dto.KomgaLibraryDto
import com.koma.client.data.server.komga.dto.KomgaPageDto
import com.koma.client.data.server.komga.dto.KomgaPageWrapper
import com.koma.client.data.server.komga.dto.KomgaReadProgressUpdateDto
import com.koma.client.data.server.komga.dto.KomgaSeriesDto
import com.koma.client.data.server.komga.dto.KomgaUserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface KomgaApi {

    @GET("api/v1/users/me")
    suspend fun getMe(): KomgaUserDto

    @GET("api/v1/libraries")
    suspend fun getLibraries(): List<KomgaLibraryDto>

    @GET("api/v1/series")
    suspend fun getSeries(
        @Query("library_id") libraryId: String? = null,
        @Query("status") readStatus: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): KomgaPageWrapper<KomgaSeriesDto>

    @GET("api/v1/series/{seriesId}")
    suspend fun getSeriesDetail(@Path("seriesId") seriesId: String): KomgaSeriesDto

    @GET("api/v1/series/{seriesId}/books")
    suspend fun getSeriesBooks(
        @Path("seriesId") seriesId: String,
        @Query("sort") sort: String? = "metadata.numberSort,asc",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 500,
    ): KomgaPageWrapper<KomgaBookDto>

    @GET("api/v1/books/{bookId}")
    suspend fun getBook(@Path("bookId") bookId: String): KomgaBookDto

    @GET("api/v1/books/{bookId}/pages")
    suspend fun getBookPages(@Path("bookId") bookId: String): List<KomgaPageDto>

    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(
        @Path("bookId") bookId: String,
        @Body body: KomgaReadProgressUpdateDto,
    )

    @GET("api/v1/series/new")
    suspend fun getNewSeries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): KomgaPageWrapper<KomgaSeriesDto>

    @GET("api/v1/books/ondeck")
    suspend fun getOnDeck(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): KomgaPageWrapper<KomgaBookDto>
}
```

Note: thumbnail and page image URLs are constructed directly (not Retrofit calls) because they're loaded by Coil as image URLs:
- Series thumbnail: `{baseUrl}/api/v1/series/{id}/thumbnail`
- Book thumbnail: `{baseUrl}/api/v1/books/{id}/thumbnail`
- Book page: `{baseUrl}/api/v1/books/{id}/pages/{n}`
- Book file: `{baseUrl}/api/v1/books/{id}/file`

- [ ] **Step 2: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/koma/client/data/server/komga/KomgaApi.kt
git commit -m "feat: add KomgaApi Retrofit service"
```

---

### Task 4: Komga DTO → domain model mappers (with tests)

**Files:**
- Create: `app/src/main/java/com/koma/client/data/server/komga/KomgaMapper.kt`
- Test: `app/src/test/java/com/koma/client/data/server/komga/KomgaMapperTest.kt`

- [ ] **Step 1: Write failing mapper tests**

```kotlin
package com.koma.client.data.server.komga

import com.google.common.truth.Truth.assertThat
import com.koma.client.data.server.komga.dto.KomgaBookDto
import com.koma.client.data.server.komga.dto.KomgaBookMetadataDto
import com.koma.client.data.server.komga.dto.KomgaLibraryDto
import com.koma.client.data.server.komga.dto.KomgaMediaDto
import com.koma.client.data.server.komga.dto.KomgaReadProgressDto
import com.koma.client.data.server.komga.dto.KomgaSeriesDto
import com.koma.client.domain.model.MediaType
import org.junit.Test

class KomgaMapperTest {

    private val baseUrl = "https://komga.local"
    private val serverId = "server1"

    @Test
    fun library_maps_id_and_name() {
        val dto = KomgaLibraryDto(id = "lib1", name = "Manga")
        val result = dto.toDomain(serverId)
        assertThat(result.id).isEqualTo("lib1")
        assertThat(result.serverId).isEqualTo(serverId)
        assertThat(result.name).isEqualTo("Manga")
    }

    @Test
    fun series_maps_counts_and_thumb() {
        val dto = KomgaSeriesDto(
            id = "s1",
            libraryId = "lib1",
            name = "One Piece",
            booksCount = 100,
            booksUnreadCount = 5,
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.id).isEqualTo("s1")
        assertThat(result.title).isEqualTo("One Piece")
        assertThat(result.bookCount).isEqualTo(100)
        assertThat(result.unreadCount).isEqualTo(5)
        assertThat(result.thumbUrl).isEqualTo("$baseUrl/api/v1/series/s1/thumbnail")
    }

    @Test
    fun book_maps_mediaType_image_for_zip() {
        val dto = KomgaBookDto(
            id = "b1",
            seriesId = "s1",
            name = "Chapter 1",
            number = 1,
            sizeBytes = 5_000_000,
            media = KomgaMediaDto(mediaType = "application/zip", pagesCount = 20),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.mediaType).isEqualTo(MediaType.IMAGE)
        assertThat(result.pageCount).isEqualTo(20)
        assertThat(result.thumbUrl).isEqualTo("$baseUrl/api/v1/books/b1/thumbnail")
    }

    @Test
    fun book_maps_mediaType_epub() {
        val dto = KomgaBookDto(
            id = "b2",
            seriesId = "s1",
            name = "Novel",
            media = KomgaMediaDto(mediaType = "application/epub+zip", pagesCount = 0),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.mediaType).isEqualTo(MediaType.EPUB)
    }

    @Test
    fun book_maps_mediaType_pdf() {
        val dto = KomgaBookDto(
            id = "b3",
            seriesId = "s1",
            name = "PDF book",
            media = KomgaMediaDto(mediaType = "application/pdf", pagesCount = 50),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.mediaType).isEqualTo(MediaType.PDF)
    }

    @Test
    fun book_uses_metadata_title_when_available() {
        val dto = KomgaBookDto(
            id = "b1",
            seriesId = "s1",
            name = "file_001.cbz",
            metadata = KomgaBookMetadataDto(title = "Chapter 1: The Beginning"),
            media = KomgaMediaDto(pagesCount = 20),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.title).isEqualTo("Chapter 1: The Beginning")
    }

    @Test
    fun book_falls_back_to_name_when_metadata_title_empty() {
        val dto = KomgaBookDto(
            id = "b1",
            seriesId = "s1",
            name = "file_001.cbz",
            metadata = KomgaBookMetadataDto(title = ""),
            media = KomgaMediaDto(pagesCount = 20),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.title).isEqualTo("file_001.cbz")
    }

    @Test
    fun readProgress_maps_correctly() {
        val dto = KomgaReadProgressDto(page = 15, completed = false)
        val result = dto.toDomain("b1")
        assertThat(result.bookId).isEqualTo("b1")
        assertThat(result.page).isEqualTo(15)
        assertThat(result.completed).isFalse()
    }
}
```

- [ ] **Step 2: Run test to verify compile failure**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.data.server.komga.KomgaMapperTest"
```
Expected: FAIL — `toDomain` unresolved.

- [ ] **Step 3: Create `KomgaMapper.kt`**

```kotlin
package com.koma.client.data.server.komga

import com.koma.client.data.server.komga.dto.KomgaBookDto
import com.koma.client.data.server.komga.dto.KomgaLibraryDto
import com.koma.client.data.server.komga.dto.KomgaReadProgressDto
import com.koma.client.data.server.komga.dto.KomgaSeriesDto
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.MediaType
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.model.Series

fun KomgaLibraryDto.toDomain(serverId: String) = Library(
    id = id,
    serverId = serverId,
    name = name,
)

fun KomgaSeriesDto.toDomain(serverId: String, baseUrl: String) = Series(
    id = id,
    serverId = serverId,
    libraryId = libraryId,
    title = name,
    bookCount = booksCount,
    unreadCount = booksUnreadCount,
    thumbUrl = "$baseUrl/api/v1/series/$id/thumbnail",
    summary = booksMetadata.summary.ifBlank { metadata.summary }.ifBlank { null },
)

fun KomgaBookDto.toDomain(serverId: String, baseUrl: String) = Book(
    id = id,
    serverId = serverId,
    seriesId = seriesId,
    title = metadata.title.ifBlank { name },
    pageCount = media.pagesCount,
    mediaType = media.mediaType.toKomaMediaType(),
    number = number,
    sizeBytes = sizeBytes,
    thumbUrl = "$baseUrl/api/v1/books/$id/thumbnail",
)

fun KomgaReadProgressDto.toDomain(bookId: String) = ReadProgress(
    bookId = bookId,
    page = page,
    completed = completed,
)

private fun String.toKomaMediaType(): MediaType = when {
    contains("epub") -> MediaType.EPUB
    contains("pdf") -> MediaType.PDF
    else -> MediaType.IMAGE
}
```

- [ ] **Step 4: Run test to verify PASS**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.data.server.komga.KomgaMapperTest"
```
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koma/client/data/server/komga/KomgaMapper.kt app/src/test/java/com/koma/client/data/server/komga/
git commit -m "feat: add Komga DTO to domain model mappers with tests"
```

---

### Task 5: OkHttp client, Hilt networking module, Coil setup

**Files:**
- Create: `app/src/main/java/com/koma/client/di/NetworkModule.kt`
- Create: `app/src/main/java/com/koma/client/data/auth/CredentialStore.kt`

- [ ] **Step 1: Create `CredentialStore.kt`**

Wraps EncryptedSharedPreferences for storing credentials per server.

```kotlin
package com.koma.client.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "koma_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getUsername(serverId: String): String? = prefs.getString("${serverId}_user", null)
    fun getPassword(serverId: String): String? = prefs.getString("${serverId}_pass", null)

    fun store(serverId: String, username: String, password: String) {
        prefs.edit()
            .putString("${serverId}_user", username)
            .putString("${serverId}_pass", password)
            .apply()
    }

    fun delete(serverId: String) {
        prefs.edit()
            .remove("${serverId}_user")
            .remove("${serverId}_pass")
            .apply()
    }
}
```

- [ ] **Step 2: Create `NetworkModule.kt`**

Provides a base OkHttp client, and a factory to create per-server authenticated Retrofit instances.

```kotlin
package com.koma.client.di

import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.server.komga.KomgaApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    @BaseOkHttpClient
    fun provideBaseOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()
}
```

- [ ] **Step 3: Verify build**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/koma/client/data/auth/ app/src/main/java/com/koma/client/di/NetworkModule.kt
git commit -m "feat: add CredentialStore and OkHttp networking module"
```

---

### Task 6: KomgaMediaServer implementation

**Files:**
- Create: `app/src/main/java/com/koma/client/data/server/komga/KomgaMediaServer.kt`
- Create: `app/src/main/java/com/koma/client/data/server/MediaServerRegistry.kt`

- [ ] **Step 1: Create `KomgaMediaServer.kt`**

```kotlin
package com.koma.client.data.server.komga

import com.koma.client.data.auth.CredentialStore
import com.koma.client.di.BaseOkHttpClient
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Library
import com.koma.client.domain.model.ReadProgress
import com.koma.client.domain.model.Series
import com.koma.client.domain.model.ThumbKind
import com.koma.client.domain.server.MediaServer
import com.koma.client.domain.server.MediaServerType
import com.koma.client.domain.server.SearchFilter
import com.koma.client.domain.server.SearchResults
import com.koma.client.domain.server.SeriesFilter
import com.koma.client.domain.server.ServerCapabilities
import com.koma.client.data.server.komga.dto.KomgaReadProgressUpdateDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class KomgaMediaServer(
    override val id: String,
    private val baseUrl: String,
    private val credentialStore: CredentialStore,
    private val baseOkHttpClient: OkHttpClient,
    private val json: Json,
) : MediaServer {

    override val type: MediaServerType = MediaServerType.KOMGA
    override val capabilities: ServerCapabilities = ServerCapabilities.komgaDefaults()

    private val okHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val username = credentialStore.getUsername(id) ?: ""
                val password = credentialStore.getPassword(id) ?: ""
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val api: KomgaApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KomgaApi::class.java)
    }

    /** Authenticated OkHttpClient for Coil image loading */
    fun authenticatedClient(): OkHttpClient = okHttpClient

    override suspend fun authenticate(): Result<Unit> = runCatching {
        api.getMe()
        Unit
    }

    override fun libraries(): Flow<List<Library>> = flow {
        val libs = api.getLibraries().map { it.toDomain(id) }
        emit(libs)
    }

    override fun series(libraryId: String, filter: SeriesFilter): Flow<List<Series>> = flow {
        val sort = when (filter.sortBy) {
            com.koma.client.domain.server.SeriesSort.TITLE -> "metadata.titleSort,asc"
            com.koma.client.domain.server.SeriesSort.DATE_ADDED -> "created,desc"
            com.koma.client.domain.server.SeriesSort.LAST_READ -> "lastModified,desc"
        }
        val readStatus = when (filter.readStatus) {
            SeriesFilter.ReadStatus.ALL -> null
            SeriesFilter.ReadStatus.UNREAD -> "UNREAD"
            SeriesFilter.ReadStatus.IN_PROGRESS -> "IN_PROGRESS"
            SeriesFilter.ReadStatus.COMPLETED -> "READ"
        }
        // Load first page; pagination support via Paging3 is deferred
        val page = api.getSeries(
            libraryId = libraryId,
            readStatus = readStatus,
            sort = sort,
            page = 0,
            size = 500,
        )
        emit(page.content.map { it.toDomain(id, baseUrl) })
    }

    override suspend fun seriesDetail(id: String): Series {
        return api.getSeriesDetail(id).toDomain(this.id, baseUrl)
    }

    override fun books(seriesId: String): Flow<List<Book>> = flow {
        val page = api.getSeriesBooks(seriesId)
        emit(page.content.map { it.toDomain(id, baseUrl) })
    }

    override suspend fun book(id: String): Book {
        return api.getBook(id).toDomain(this.id, baseUrl)
    }

    override suspend fun pageUrl(bookId: String, page: Int): String =
        "${baseUrl.trimEnd('/')}/api/v1/books/$bookId/pages/$page"

    override suspend fun fileUrl(bookId: String): String =
        "${baseUrl.trimEnd('/')}/api/v1/books/$bookId/file"

    override suspend fun thumbnailUrl(id: String, kind: ThumbKind): String {
        val segment = when (kind) {
            ThumbKind.SERIES -> "series"
            ThumbKind.BOOK -> "books"
            ThumbKind.LIBRARY -> "libraries"
        }
        return "${baseUrl.trimEnd('/')}/api/v1/$segment/$id/thumbnail"
    }

    override suspend fun updateProgress(bookId: String, progress: ReadProgress) {
        api.updateReadProgress(bookId, KomgaReadProgressUpdateDto(
            page = progress.page,
            completed = progress.completed,
        ))
    }

    override suspend fun search(query: String, filter: SearchFilter): SearchResults {
        // Simple search — POST search endpoint is complex; use GET series with unpaged for now
        val series = api.getSeries(page = 0, size = 50).content
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toDomain(id, baseUrl) }
        return SearchResults(series = series, books = emptyList())
    }
}
```

- [ ] **Step 2: Create `MediaServerRegistry.kt`**

Resolves a `servers` row into a live `MediaServer` instance. Caches instances by server ID.

```kotlin
package com.koma.client.data.server

import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.db.entity.ServerEntity
import com.koma.client.data.server.komga.KomgaMediaServer
import com.koma.client.di.BaseOkHttpClient
import com.koma.client.domain.server.MediaServer
import com.koma.client.domain.server.MediaServerType
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaServerRegistry @Inject constructor(
    private val credentialStore: CredentialStore,
    @BaseOkHttpClient private val baseOkHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val cache = mutableMapOf<String, MediaServer>()

    fun get(server: ServerEntity): MediaServer = cache.getOrPut(server.id) {
        when (server.type) {
            MediaServerType.KOMGA -> KomgaMediaServer(
                id = server.id,
                baseUrl = server.baseUrl,
                credentialStore = credentialStore,
                baseOkHttpClient = baseOkHttpClient,
                json = json,
            )
            MediaServerType.KAVITA -> TODO("Kavita support — Plan 6")
            MediaServerType.CALIBRE_WEB -> TODO("Calibre-Web support — Plan 7")
        }
    }

    fun evict(serverId: String) {
        cache.remove(serverId)
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/koma/client/data/server/
git commit -m "feat: add KomgaMediaServer and MediaServerRegistry"
```

---

### Task 7: Expand Room — cache tables for libraries, series, books

**Files:**
- Create: `app/src/main/java/com/koma/client/data/db/entity/CachedLibraryEntity.kt`
- Create: `app/src/main/java/com/koma/client/data/db/entity/CachedSeriesEntity.kt`
- Create: `app/src/main/java/com/koma/client/data/db/entity/CachedBookEntity.kt`
- Create: `app/src/main/java/com/koma/client/data/db/dao/LibraryDao.kt`
- Create: `app/src/main/java/com/koma/client/data/db/dao/SeriesDao.kt`
- Create: `app/src/main/java/com/koma/client/data/db/dao/BookDao.kt`
- Modify: `app/src/main/java/com/koma/client/data/db/KomaDatabase.kt`
- Modify: `app/src/main/java/com/koma/client/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/koma/client/data/db/LibraryDaoTest.kt`

- [ ] **Step 1: Write failing DAO test**

```kotlin
package com.koma.client.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.koma.client.data.db.entity.CachedLibraryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryDaoTest {

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
    fun replaceAll_clears_and_inserts() = runTest {
        val dao = db.libraryDao()
        val libs = listOf(
            CachedLibraryEntity(id = "l1", serverId = "s1", name = "Manga"),
            CachedLibraryEntity(id = "l2", serverId = "s1", name = "Comics"),
        )
        dao.replaceAll("s1", libs)
        dao.observeByServer("s1").test {
            val items = awaitItem()
            assertThat(items).hasSize(2)
            assertThat(items.map { it.name }).containsExactly("Comics", "Manga")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun replaceAll_scoped_to_server() = runTest {
        val dao = db.libraryDao()
        dao.replaceAll("s1", listOf(CachedLibraryEntity("l1", "s1", "Manga")))
        dao.replaceAll("s2", listOf(CachedLibraryEntity("l2", "s2", "Books")))

        dao.observeByServer("s1").test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
        // Now replace s1 — should not affect s2
        dao.replaceAll("s1", emptyList())
        dao.observeByServer("s2").test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify compile failure**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.data.db.LibraryDaoTest"
```

- [ ] **Step 3: Create entities**

`CachedLibraryEntity.kt`:
```kotlin
package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_libraries")
data class CachedLibraryEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val name: String,
)
```

`CachedSeriesEntity.kt`:
```kotlin
package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_series")
data class CachedSeriesEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val libraryId: String,
    val title: String,
    val bookCount: Int = 0,
    val unreadCount: Int = 0,
    val thumbUrl: String? = null,
    val summary: String? = null,
)
```

`CachedBookEntity.kt`:
```kotlin
package com.koma.client.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_books")
data class CachedBookEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val seriesId: String,
    val title: String,
    val number: Int? = null,
    val pageCount: Int = 0,
    val mediaType: String = "IMAGE",
    val sizeBytes: Long? = null,
    val thumbUrl: String? = null,
)
```

- [ ] **Step 4: Create DAOs**

`LibraryDao.kt`:
```kotlin
package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.koma.client.data.db.entity.CachedLibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryDao {

    @Query("SELECT * FROM cached_libraries WHERE serverId = :serverId ORDER BY name")
    abstract fun observeByServer(serverId: String): Flow<List<CachedLibraryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(libraries: List<CachedLibraryEntity>)

    @Query("DELETE FROM cached_libraries WHERE serverId = :serverId")
    abstract suspend fun deleteByServer(serverId: String)

    @Transaction
    open suspend fun replaceAll(serverId: String, libraries: List<CachedLibraryEntity>) {
        deleteByServer(serverId)
        insertAll(libraries)
    }
}
```

`SeriesDao.kt`:
```kotlin
package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.koma.client.data.db.entity.CachedSeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SeriesDao {

    @Query("SELECT * FROM cached_series WHERE libraryId = :libraryId ORDER BY title")
    abstract fun observeByLibrary(libraryId: String): Flow<List<CachedSeriesEntity>>

    @Query("SELECT * FROM cached_series WHERE id = :id")
    abstract suspend fun getById(id: String): CachedSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(series: List<CachedSeriesEntity>)

    @Query("DELETE FROM cached_series WHERE libraryId = :libraryId")
    abstract suspend fun deleteByLibrary(libraryId: String)

    @Transaction
    open suspend fun replaceByLibrary(libraryId: String, series: List<CachedSeriesEntity>) {
        deleteByLibrary(libraryId)
        insertAll(series)
    }
}
```

`BookDao.kt`:
```kotlin
package com.koma.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.koma.client.data.db.entity.CachedBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookDao {

    @Query("SELECT * FROM cached_books WHERE seriesId = :seriesId ORDER BY number")
    abstract fun observeBySeries(seriesId: String): Flow<List<CachedBookEntity>>

    @Query("SELECT * FROM cached_books WHERE id = :id")
    abstract suspend fun getById(id: String): CachedBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(books: List<CachedBookEntity>)

    @Query("DELETE FROM cached_books WHERE seriesId = :seriesId")
    abstract suspend fun deleteBySeries(seriesId: String)

    @Transaction
    open suspend fun replaceBySeries(seriesId: String, books: List<CachedBookEntity>) {
        deleteBySeries(seriesId)
        insertAll(books)
    }
}
```

- [ ] **Step 5: Update `KomaDatabase.kt` to include new entities and DAOs**

Add `CachedLibraryEntity`, `CachedSeriesEntity`, `CachedBookEntity` to the `entities` array. Add abstract fun `libraryDao()`, `seriesDao()`, `bookDao()`. **Increment the database version to 2** and add a destructive migration fallback (v1 had no real user data).

```kotlin
@Database(
    entities = [
        ServerEntity::class,
        CachedLibraryEntity::class,
        CachedSeriesEntity::class,
        CachedBookEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(MediaServerTypeConverters::class)
abstract class KomaDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun libraryDao(): LibraryDao
    abstract fun seriesDao(): SeriesDao
    abstract fun bookDao(): BookDao
}
```

In `DatabaseModule.kt`, add `.fallbackToDestructiveMigration()` to the builder (acceptable since v1 had no real user data) and provide the new DAOs:

```kotlin
@Provides
@Singleton
fun provideDatabase(@ApplicationContext context: Context): KomaDatabase =
    Room.databaseBuilder(context, KomaDatabase::class.java, "koma.db")
        .fallbackToDestructiveMigration()
        .build()

@Provides fun provideLibraryDao(db: KomaDatabase): LibraryDao = db.libraryDao()
@Provides fun provideSeriesDao(db: KomaDatabase): SeriesDao = db.seriesDao()
@Provides fun provideBookDao(db: KomaDatabase): BookDao = db.bookDao()
```

- [ ] **Step 6: Run test to verify PASS**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.data.db.LibraryDaoTest"
```
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/koma/client/data/db/ app/src/test/java/com/koma/client/data/db/LibraryDaoTest.kt app/src/main/java/com/koma/client/di/DatabaseModule.kt
git commit -m "feat: add Room cache tables for libraries, series, books"
```

---

### Task 8: Expand ServerRepository + add ServerDao update/setActive

**Files:**
- Modify: `app/src/main/java/com/koma/client/data/db/dao/ServerDao.kt` — add `update`, `setActive`
- Modify: `app/src/main/java/com/koma/client/domain/repo/ServerRepository.kt` — add insert, delete, setActive
- Modify: `app/src/main/java/com/koma/client/data/repo/ServerRepositoryImpl.kt` — implement new methods

- [ ] **Step 1: Add DAO operations**

Add to `ServerDao.kt`:
```kotlin
@Query("UPDATE servers SET isActive = 0")
suspend fun deactivateAll()

@Transaction
open suspend fun setActive(id: String) {
    deactivateAll()
    activateOne(id)
}

@Query("UPDATE servers SET isActive = 1 WHERE id = :id")
abstract suspend fun activateOne(id: String)
```

Note: `deactivateAll()` must also be `abstract`. The `setActive` method is `open` with a `@Transaction`. The DAO itself is already `interface` — change it to `abstract class` to support `@Transaction` open methods.

- [ ] **Step 2: Expand ServerRepository interface**

```kotlin
package com.koma.client.domain.repo

import com.koma.client.domain.model.Server
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    fun observeServers(): Flow<List<Server>>
    suspend fun getActive(): Server?
    suspend fun getById(id: String): Server?
    suspend fun insert(server: Server, username: String, password: String)
    suspend fun delete(serverId: String)
    suspend fun setActive(serverId: String)
}
```

- [ ] **Step 3: Update ServerRepositoryImpl**

The `insert` method needs access to `CredentialStore` to securely store credentials, and builds a `ServerEntity` from the domain `Server`. Add `CredentialStore` to the constructor.

```kotlin
class ServerRepositoryImpl @Inject constructor(
    private val dao: ServerDao,
    private val credentialStore: CredentialStore,
) : ServerRepository {

    override fun observeServers(): Flow<List<Server>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getActive(): Server? = dao.getActive()?.toDomain()

    override suspend fun getById(id: String): Server? = dao.getById(id)?.toDomain()

    override suspend fun insert(server: Server, username: String, password: String) {
        credentialStore.store(server.id, username, password)
        dao.insert(ServerEntity(
            id = server.id,
            type = server.type,
            name = server.name,
            baseUrl = server.baseUrl,
            username = username,
            encPassword = "",  // actual password in EncryptedSharedPreferences
            encJwtToken = null,
            encSessionCookie = null,
            isActive = server.isActive,
            lastSyncAtEpochMs = System.currentTimeMillis(),
        ))
    }

    override suspend fun delete(serverId: String) {
        credentialStore.delete(serverId)
        dao.delete(serverId)
    }

    override suspend fun setActive(serverId: String) = dao.setActive(serverId)
}

private fun ServerEntity.toDomain() = Server(
    id = id,
    type = type,
    name = name,
    baseUrl = baseUrl,
    isActive = isActive,
)
```

- [ ] **Step 4: Fix RepositoryModule if needed — update Hilt binding**

No change needed — `@Binds` already maps `ServerRepositoryImpl` → `ServerRepository`. Hilt auto-discovers the new `CredentialStore` injection.

- [ ] **Step 5: Run full test suite**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest
```

Note: `ServerRepositoryImplTest` may need updating since the constructor now takes `CredentialStore`. Fix the test by either: (a) creating a fake `CredentialStore` in a `@Before` that uses a temp `SharedPreferences`, or (b) adjusting the test to only test the `observeServers`/`getActive` paths that don't need `CredentialStore`. Option (b) is simpler and keeps the test focused.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/koma/client/data/db/dao/ServerDao.kt app/src/main/java/com/koma/client/domain/repo/ServerRepository.kt app/src/main/java/com/koma/client/data/repo/ServerRepositoryImpl.kt
git commit -m "feat: expand ServerRepository with insert, delete, setActive"
```

---

### Task 9: Add Server flow — UI + ViewModel

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/auth/AddServerScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/koma/client/ui/auth/AddServerViewModelTest.kt`

- [ ] **Step 1: Add string resources**

Add to `strings.xml`:
```xml
<string name="add_server_title">Add Server</string>
<string name="add_server_type_label">Server Type</string>
<string name="add_server_url_label">Server URL</string>
<string name="add_server_url_hint">https://komga.example.com</string>
<string name="add_server_username_label">Username</string>
<string name="add_server_password_label">Password</string>
<string name="add_server_name_label">Display Name (optional)</string>
<string name="add_server_test_button">Test Connection</string>
<string name="add_server_save_button">Save</string>
<string name="add_server_testing">Testing connection…</string>
<string name="add_server_success">Connected!</string>
<string name="add_server_error">Connection failed: %1$s</string>
<string name="label_komga">Komga</string>
<string name="label_kavita">Kavita</string>
<string name="label_calibre_web">Calibre-Web</string>
```

- [ ] **Step 2: Write failing ViewModel test**

```kotlin
package com.koma.client.ui.auth

import com.google.common.truth.Truth.assertThat
import com.koma.client.domain.model.Server
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.MediaServer
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
        override fun observeServers() = _flow
        override suspend fun getActive() = servers.firstOrNull { it.isActive }
        override suspend fun getById(id: String) = servers.firstOrNull { it.id == id }
        override suspend fun insert(server: Server, username: String, password: String) {
            servers.add(server)
            _flow.value = servers.toList()
        }
        override suspend fun delete(serverId: String) {
            servers.removeAll { it.id == serverId }
            _flow.value = servers.toList()
        }
        override suspend fun setActive(serverId: String) {}
    }

    @Test
    fun initial_state_has_komga_selected() = runTest {
        val vm = AddServerViewModel(FakeRepo(), FakeMediaServerFactory())
        assertThat(vm.uiState.value.selectedType).isEqualTo(MediaServerType.KOMGA)
        assertThat(vm.uiState.value.isTesting).isFalse()
    }

    @Test
    fun save_adds_server_to_repo() = runTest {
        val repo = FakeRepo()
        val vm = AddServerViewModel(repo, FakeMediaServerFactory())
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
    }
}
```

- [ ] **Step 3: Create `AddServerScreen.kt` with ViewModel, state, UI, and `MediaServerFactory` interface**

This file contains:
- `MediaServerFactory` interface (used by the ViewModel to create a temporary `MediaServer` for testing connections, injected via Hilt)
- `AddServerUiState` data class
- `AddServerViewModel` @HiltViewModel
- `AddServerScreen` @Composable

```kotlin
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
import com.koma.client.domain.model.Server
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.MediaServer
import com.koma.client.domain.server.MediaServerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

interface MediaServerFactory {
    fun create(
        type: MediaServerType,
        id: String,
        baseUrl: String,
        username: String,
        password: String,
    ): MediaServer
}

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
        _uiState.update { it.copy(isTesting = true, testResult = null) }
        viewModelScope.launch {
            val server = factory.create(s.selectedType, "test", s.url, s.username, s.password)
            val result = server.authenticate()
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
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val name = s.displayName.ifBlank { "${s.selectedType.name} - ${s.url}" }
            serverRepo.insert(
                Server(id = id, type = s.selectedType, name = name, baseUrl = s.url, isActive = true),
                s.username,
                s.password,
            )
            serverRepo.setActive(id)
            _uiState.update { it.copy(saved = true) }
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
            MediaServerType.values().forEach { type ->
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
```

- [ ] **Step 4: Create `MediaServerFactory` Hilt implementation**

Create `app/src/main/java/com/koma/client/di/MediaServerFactoryModule.kt`:

```kotlin
package com.koma.client.di

import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.server.komga.KomgaMediaServer
import com.koma.client.domain.server.MediaServer
import com.koma.client.domain.server.MediaServerType
import com.koma.client.ui.auth.MediaServerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaServerFactoryModule {

    @Provides
    @Singleton
    fun provideMediaServerFactory(
        credentialStore: CredentialStore,
        @BaseOkHttpClient baseOkHttpClient: OkHttpClient,
        json: Json,
    ): MediaServerFactory = object : MediaServerFactory {
        override fun create(
            type: MediaServerType,
            id: String,
            baseUrl: String,
            username: String,
            password: String,
        ): MediaServer {
            // For test connections, store credentials temporarily
            credentialStore.store(id, username, password)
            return when (type) {
                MediaServerType.KOMGA -> KomgaMediaServer(id, baseUrl, credentialStore, baseOkHttpClient, json)
                MediaServerType.KAVITA -> TODO("Kavita — Plan 6")
                MediaServerType.CALIBRE_WEB -> TODO("Calibre-Web — Plan 7")
            }
        }
    }
}
```

- [ ] **Step 5: Run ViewModel test**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.ui.auth.AddServerViewModelTest"
```
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/auth/ app/src/main/java/com/koma/client/di/MediaServerFactoryModule.kt app/src/main/res/values/strings.xml app/src/test/java/com/koma/client/ui/auth/
git commit -m "feat: add server flow with AddServerScreen and ViewModel"
```

---

### Task 10: Library browser screen

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/library/LibraryScreen.kt`

- [ ] **Step 1: Create `LibraryScreen.kt`**

Contains `LibraryViewModel` and `LibraryScreen` composable. The ViewModel observes the active server's libraries via `MediaServerRegistry`.

```kotlin
package com.koma.client.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Library
import com.koma.client.domain.repo.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Loaded(val libraries: List<Library>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init { loadLibraries() }

    fun loadLibraries() {
        viewModelScope.launch {
            _state.value = LibraryUiState.Loading
            try {
                val server = serverRepo.getActive()
                if (server == null) {
                    _state.value = LibraryUiState.Error("No active server")
                    return@launch
                }
                val entity = com.koma.client.data.db.entity.ServerEntity(
                    id = server.id, type = server.type, name = server.name,
                    baseUrl = server.baseUrl, username = "", encPassword = "",
                    encJwtToken = null, encSessionCookie = null,
                    isActive = server.isActive, lastSyncAtEpochMs = 0L,
                )
                val mediaServer = registry.get(entity)
                mediaServer.libraries().collect { libs ->
                    _state.value = LibraryUiState.Loaded(libs)
                }
            } catch (e: Exception) {
                _state.value = LibraryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

@Composable
fun LibraryScreen(
    onLibraryClick: (libraryId: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        LibraryUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is LibraryUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::loadLibraries) { Text("Retry") }
                }
            }
        }
        is LibraryUiState.Loaded -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.libraries, key = { it.id }) { library ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLibraryClick(library.id) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(library.name, style = MaterialTheme.typography.titleMedium)
                            if (library.unreadCount > 0) {
                                Text(
                                    "${library.unreadCount} unread",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Note: This directly constructs a `ServerEntity` to pass to the registry, which is not ideal. This is a known simplification — a future refactor (Task for Plan 2 cleanup) should have `MediaServerRegistry.getForActive()` that internally resolves the entity. For now, this works.

- [ ] **Step 2: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/library/
git commit -m "feat: add LibraryScreen with grid of libraries"
```

---

### Task 11: Series list screen

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/series/SeriesListScreen.kt`

- [ ] **Step 1: Create `SeriesListScreen.kt`**

Contains `SeriesListViewModel` (takes `libraryId` as SavedStateHandle arg) and `SeriesListScreen` composable with grid, filter chips, and thumbnails via Coil.

```kotlin
package com.koma.client.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Series
import com.koma.client.domain.repo.ServerRepository
import com.koma.client.domain.server.SeriesFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SeriesListUiState {
    data object Loading : SeriesListUiState
    data class Loaded(val series: List<Series>, val filter: SeriesFilter) : SeriesListUiState
    data class Error(val message: String) : SeriesListUiState
}

@HiltViewModel
class SeriesListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
) : ViewModel() {

    private val libraryId: String = savedStateHandle["libraryId"] ?: ""
    private val _state = MutableStateFlow<SeriesListUiState>(SeriesListUiState.Loading)
    val state: StateFlow<SeriesListUiState> = _state.asStateFlow()
    private var currentFilter = SeriesFilter()

    init { load() }

    fun setFilter(filter: SeriesFilter) {
        currentFilter = filter
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = SeriesListUiState.Loading
            try {
                val server = serverRepo.getActive() ?: run {
                    _state.value = SeriesListUiState.Error("No active server")
                    return@launch
                }
                val entity = com.koma.client.data.db.entity.ServerEntity(
                    id = server.id, type = server.type, name = server.name,
                    baseUrl = server.baseUrl, username = "", encPassword = "",
                    encJwtToken = null, encSessionCookie = null,
                    isActive = server.isActive, lastSyncAtEpochMs = 0L,
                )
                val mediaServer = registry.get(entity)
                mediaServer.series(libraryId, currentFilter).collect { list ->
                    _state.value = SeriesListUiState.Loaded(list, currentFilter)
                }
            } catch (e: Exception) {
                _state.value = SeriesListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

@Composable
fun SeriesListScreen(
    onSeriesClick: (seriesId: String) -> Unit,
    viewModel: SeriesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        if (state is SeriesListUiState.Loaded) {
            val current = (state as SeriesListUiState.Loaded).filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeriesFilter.ReadStatus.values().forEach { status ->
                    FilterChip(
                        selected = current.readStatus == status,
                        onClick = { viewModel.setFilter(current.copy(readStatus = status)) },
                        label = { Text(status.name.replace("_", " ")) },
                    )
                }
            }
        }

        when (val s = state) {
            SeriesListUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is SeriesListUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is SeriesListUiState.Loaded -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.series, key = { it.id }) { series ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeriesClick(series.id) },
                        ) {
                            Column {
                                AsyncImage(
                                    model = series.thumbUrl,
                                    contentDescription = series.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f),
                                )
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        series.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                    )
                                    if (series.unreadCount > 0) {
                                        Badge { Text("${series.unreadCount}") }
                                    }
                                }
                            }
                        }
                    }
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
git add app/src/main/java/com/koma/client/ui/series/SeriesListScreen.kt
git commit -m "feat: add SeriesListScreen with grid, thumbnails, and filters"
```

---

### Task 12: Series detail screen

**Files:**
- Create: `app/src/main/java/com/koma/client/ui/series/SeriesDetailScreen.kt`

- [ ] **Step 1: Create `SeriesDetailScreen.kt`**

Contains `SeriesDetailViewModel` (takes `seriesId`) and composable showing series metadata header + scrollable book list.

```kotlin
package com.koma.client.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.koma.client.data.server.MediaServerRegistry
import com.koma.client.domain.model.Book
import com.koma.client.domain.model.Series
import com.koma.client.domain.repo.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailUiState(
    val loading: Boolean = true,
    val series: Series? = null,
    val books: List<Book> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepo: ServerRepository,
    private val registry: MediaServerRegistry,
) : ViewModel() {

    private val seriesId: String = savedStateHandle["seriesId"] ?: ""
    private val _state = MutableStateFlow(SeriesDetailUiState())
    val state: StateFlow<SeriesDetailUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
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
                val series = mediaServer.seriesDetail(seriesId)
                _state.update { it.copy(series = series) }

                mediaServer.books(seriesId).collect { books ->
                    _state.update { it.copy(loading = false, books = books) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(
    onBookClick: (bookId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading && state.series == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    state.error?.let { err ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(err, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val series = state.series ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AsyncImage(
                    model = series.thumbUrl,
                    contentDescription = series.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(series.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${series.bookCount} books, ${series.unreadCount} unread",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    series.summary?.let { summary ->
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                            maxLines = 5,
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        // Book list
        items(state.books, key = { it.id }) { book ->
            ListItem(
                headlineContent = { Text(book.title) },
                supportingContent = {
                    Text("${book.pageCount} pages • ${book.mediaType.name}")
                },
                leadingContent = {
                    AsyncImage(
                        model = book.thumbUrl,
                        contentDescription = book.title,
                        modifier = Modifier.size(48.dp, 64.dp),
                        contentScale = ContentScale.Crop,
                    )
                },
                modifier = Modifier.clickable { onBookClick(book.id) },
            )
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
git add app/src/main/java/com/koma/client/ui/series/SeriesDetailScreen.kt
git commit -m "feat: add SeriesDetailScreen with metadata header and book list"
```

---

### Task 13: Coil setup with authenticated image loading

**Files:**
- Create: `app/src/main/java/com/koma/client/di/ImageModule.kt`

Coil needs the authenticated OkHttpClient so it can load thumbnails from Komga (which require Basic auth). We configure a global Coil `ImageLoader` via Hilt.

- [ ] **Step 1: Create `ImageModule.kt`**

```kotlin
package com.koma.client.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @BaseOkHttpClient baseOkHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { baseOkHttpClient }))
        }
        .crossfade(true)
        .build()
}
```

Note: This uses the **base** (unauthenticated) OkHttpClient initially. When we need authenticated image loading, we'll need the per-server authenticated client. For now, Coil's requests to `{baseUrl}/api/v1/series/{id}/thumbnail` will fail with 401 unless we add an interceptor.

**Fix:** We need a Coil interceptor that reads the URL's host, looks up the server credentials, and adds Basic auth. Create a simple interceptor:

Create `app/src/main/java/com/koma/client/data/auth/CoilAuthInterceptor.kt`:
```kotlin
package com.koma.client.data.auth

import com.koma.client.data.db.dao.ServerDao
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoilAuthInterceptor @Inject constructor(
    private val credentialStore: CredentialStore,
    private val serverDao: ServerDao,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // Find a server whose baseUrl matches this host
        val server = runBlocking {
            serverDao.getActive()
        }

        if (server != null && server.baseUrl.contains(host)) {
            val username = credentialStore.getUsername(server.id)
            val password = credentialStore.getPassword(server.id)
            if (username != null && password != null) {
                val authedRequest = request.newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
                return chain.proceed(authedRequest)
            }
        }

        return chain.proceed(request)
    }
}
```

Update `ImageModule.kt` to use a Coil-specific OkHttpClient with this interceptor:

```kotlin
@Provides
@Singleton
fun provideImageLoader(
    @ApplicationContext context: Context,
    @BaseOkHttpClient baseOkHttpClient: OkHttpClient,
    authInterceptor: CoilAuthInterceptor,
): ImageLoader {
    val coilClient = baseOkHttpClient.newBuilder()
        .addInterceptor(authInterceptor)
        .build()
    return ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { coilClient }))
        }
        .crossfade(true)
        .build()
}
```

- [ ] **Step 2: Set Coil as the global ImageLoader in KomaApplication**

Update `KomaApplication.kt`:
```kotlin
package com.koma.client

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KomaApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var imageLoader: ImageLoader

    override fun newImageLoader(context: android.content.Context): ImageLoader = imageLoader
}
```

- [ ] **Step 3: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/koma/client/di/ImageModule.kt app/src/main/java/com/koma/client/data/auth/CoilAuthInterceptor.kt app/src/main/java/com/koma/client/KomaApplication.kt
git commit -m "feat: add Coil image loading with auth interceptor"
```

---

### Task 14: Update navigation — new routes and screen wiring

**Files:**
- Modify: `app/src/main/java/com/koma/client/ui/nav/Routes.kt`
- Modify: `app/src/main/java/com/koma/client/ui/nav/KomaNavHost.kt`
- Modify: `app/src/main/java/com/koma/client/ui/home/HomeScreen.kt`

- [ ] **Step 1: Update Routes**

```kotlin
package com.koma.client.ui.nav

object Routes {
    const val HOME = "home"
    const val ADD_SERVER = "add_server"
    const val LIBRARIES = "libraries"
    const val SERIES_LIST = "series_list/{libraryId}"
    const val SERIES_DETAIL = "series_detail/{seriesId}"
    const val BOOK_DETAIL = "book_detail/{bookId}"  // placeholder for Plan 3

    fun seriesList(libraryId: String) = "series_list/$libraryId"
    fun seriesDetail(seriesId: String) = "series_detail/$seriesId"
    fun bookDetail(bookId: String) = "book_detail/$bookId"
}
```

- [ ] **Step 2: Update KomaNavHost**

```kotlin
package com.koma.client.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koma.client.ui.auth.AddServerScreen
import com.koma.client.ui.home.HomeScreen
import com.koma.client.ui.library.LibraryScreen
import com.koma.client.ui.series.SeriesDetailScreen
import com.koma.client.ui.series.SeriesListScreen

@Composable
fun KomaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddServer = { navController.navigate(Routes.ADD_SERVER) },
                onGoToLibraries = { navController.navigate(Routes.LIBRARIES) },
            )
        }
        composable(Routes.ADD_SERVER) {
            AddServerScreen(
                onServerAdded = {
                    navController.navigate(Routes.LIBRARIES) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }
        composable(Routes.LIBRARIES) {
            LibraryScreen(
                onLibraryClick = { libraryId -> navController.navigate(Routes.seriesList(libraryId)) },
            )
        }
        composable(Routes.SERIES_LIST) {
            SeriesListScreen(
                onSeriesClick = { seriesId -> navController.navigate(Routes.seriesDetail(seriesId)) },
            )
        }
        composable(Routes.SERIES_DETAIL) {
            SeriesDetailScreen(
                onBookClick = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BOOK_DETAIL) {
            // Placeholder — Plan 3 adds the reader
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Reader — coming in Plan 3", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
```

- [ ] **Step 3: Update HomeScreen**

Add an `onGoToLibraries` callback and show a "Go to Libraries" button when servers exist:

```kotlin
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
                Text(
                    text = "Connected: ${s.count} server(s)",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Button(onClick = onGoToLibraries, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Browse Libraries")
                }
                OutlinedButton(onClick = onAddServer, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.home_add_server))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Verify compile and full test suite**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```
Expected: All tests PASS, BUILD SUCCESSFUL.

Note: `HomeViewModelTest` may need updating since `HomeScreen` now has a new parameter `onGoToLibraries`. The test tests the ViewModel, not the composable, so it should still pass. If the composable function signature change causes compilation issues in any Compose test, add a default `onGoToLibraries = {}` parameter.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koma/client/ui/nav/ app/src/main/java/com/koma/client/ui/home/HomeScreen.kt
git commit -m "feat: wire navigation for libraries, series list, and series detail"
```

---

### Task 15: Full build verification + API test with MockWebServer

**Files:**
- Create: `app/src/test/java/com/koma/client/data/server/komga/KomgaApiTest.kt`

- [ ] **Step 1: Write MockWebServer API test**

```kotlin
package com.koma.client.data.server.komga

import com.google.common.truth.Truth.assertThat
import com.koma.client.data.server.komga.dto.KomgaLibraryDto
import com.koma.client.data.server.komga.dto.KomgaPageWrapper
import com.koma.client.data.server.komga.dto.KomgaSeriesDto
import com.koma.client.data.server.komga.dto.KomgaUserDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class KomgaApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: KomgaApi
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KomgaApi::class.java)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun getMe_parses_user() = runTest {
        server.enqueue(MockResponse()
            .setBody(json.encodeToString(KomgaUserDto(id = "u1", email = "test@example.com", roles = listOf("ADMIN"))))
            .addHeader("Content-Type", "application/json"))

        val user = api.getMe()
        assertThat(user.id).isEqualTo("u1")
        assertThat(user.email).isEqualTo("test@example.com")
    }

    @Test
    fun getLibraries_parses_list() = runTest {
        val libs = listOf(
            KomgaLibraryDto(id = "l1", name = "Manga"),
            KomgaLibraryDto(id = "l2", name = "Comics"),
        )
        server.enqueue(MockResponse()
            .setBody(json.encodeToString(libs))
            .addHeader("Content-Type", "application/json"))

        val result = api.getLibraries()
        assertThat(result).hasSize(2)
        assertThat(result[0].name).isEqualTo("Manga")
    }

    @Test
    fun getSeries_parses_page_wrapper() = runTest {
        val page = KomgaPageWrapper(
            content = listOf(
                KomgaSeriesDto(id = "s1", libraryId = "l1", name = "Naruto", booksCount = 72),
            ),
            totalElements = 1,
            totalPages = 1,
            number = 0,
            size = 20,
            first = true,
            last = true,
        )
        server.enqueue(MockResponse()
            .setBody(json.encodeToString(page))
            .addHeader("Content-Type", "application/json"))

        val result = api.getSeries(libraryId = "l1")
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].name).isEqualTo("Naruto")
        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun request_includes_correct_path_and_params() = runTest {
        server.enqueue(MockResponse()
            .setBody(json.encodeToString(KomgaPageWrapper<KomgaSeriesDto>()))
            .addHeader("Content-Type", "application/json"))

        api.getSeries(libraryId = "l1", readStatus = "UNREAD", sort = "metadata.titleSort,asc", page = 2, size = 10)
        val request = server.takeRequest()
        assertThat(request.path).contains("library_id=l1")
        assertThat(request.path).contains("status=UNREAD")
        assertThat(request.path).contains("page=2")
        assertThat(request.path).contains("size=10")
    }
}
```

- [ ] **Step 2: Run API test**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.koma.client.data.server.komga.KomgaApiTest"
```
Expected: PASS (4 tests).

- [ ] **Step 3: Run full test suite and clean build**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew clean :app:assembleDebug
```
Expected: All tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 4: Commit and tag**

```bash
git add app/src/test/java/com/koma/client/data/server/komga/KomgaApiTest.kt
git commit -m "test: add KomgaApi MockWebServer tests"
git tag -a plan-02-komga-browsing -m "Plan 2 complete: Komga backend, server add flow, library/series/book browsing"
```

---

## Plan 2 Completion Criteria

1. All existing Plan 1 tests still pass (17 tests)
2. New tests pass: `KomgaMapperTest` (8), `LibraryDaoTest` (2), `AddServerViewModelTest` (2), `KomgaApiTest` (4) — total ~33+ tests
3. `./gradlew :app:assembleDebug` produces a debug APK
4. User can launch app → "No servers yet" → tap "Add Server" → enter Komga URL + credentials → "Test Connection" → "Save" → redirected to Library grid
5. Library grid shows real libraries from the connected Komga server
6. Tapping a library shows the series grid with cover thumbnails
7. Tapping a series shows the detail screen with metadata and book list
8. Tapping a book navigates to the reader placeholder ("Reader — coming in Plan 3")
9. Credentials stored securely via EncryptedSharedPreferences
10. Git tag `plan-02-komga-browsing` marks the milestone

## What Plan 2 Deliberately Leaves Out

- No reader (Plan 3: Image Reader, Plan 4: EPUB Reader)
- No Room-backed caching for libraries/series/books (ViewModels fetch from network directly via `MediaServer.libraries()`, `series()`, `books()`) — Room cache tables are defined and DAOs exist, but repo implementations that combine network+cache are deferred to when offline support is built (Plan 8: Downloads)
- No `ServerDao.update` — only insert and delete for now
- No Paging3 integration for series listing — loads up to 500 items in one request (acceptable for most Komga libraries; pagination is deferred)
- No search, no home carousels, no downloads, no updater
- No Kavita or Calibre-Web support (Plans 6, 7)
