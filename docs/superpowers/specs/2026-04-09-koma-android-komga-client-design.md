# Koma — Android Self-Hosted Reading Client (Design Spec)

**Date:** 2026-04-09
**Status:** Approved for planning
**Reference:** Inspired by [KMReader](https://github.com/everpcpc/KMReader) (iOS, Swift)

## Overview

Koma is an Android-native client for self-hosted reading servers, supporting [Komga](https://komga.org), [Kavita](https://www.kavitareader.com), and [Calibre-Web](https://github.com/janeczku/calibre-web) in v1. Together these cover the "everything self-hosted reading" story: comics and manga (Komga, Kavita) and general ebooks (Calibre-Web). It provides feature parity with KMReader's core experience while embracing Android conventions (Material 3, Material You dynamic color) and a signature dark-blue identity. Koma supports the full range of content types served by any of these backends — manga/comics (CBZ, CBR, PDF) and EPUB — with rich reader customization for both.

Distribution is sideload-first via GitHub Releases, with an in-app updater. Play Store is a future consideration based on demand.

## Goals

- Feature parity with KMReader core functionality on Android
- First-class support for all content types served by Komga, Kavita, and Calibre-Web (images and EPUB)
- Unified UI — users shouldn't need to think about which backend a server is; terminology and flows are consistent
- Friendlier Android UX than a direct KMReader port: Material 3, dynamic color, native navigation
- Signature dark-blue brand theme with Material You fallback on Android 12+
- Offline reading via per-book downloads
- Multi-server support (mix Komga, Kavita, and Calibre-Web servers in the same app)
- Sideload distribution with in-app update checks against GitHub Releases

## Non-Goals (v1)

- Play Store release (deferred)
- iOS / desktop / web clients
- Server admin functionality (user management, library scanning triggers) for either backend
- Social features (sharing, comments)
- Writing to collections/readlists (read-only in v1)
- Kavita-specific features that have no Komga analogue (e.g. "Want to Read" lists) — deferred to v1.1
- Calibre-Web shelf management (read-only in v1)
- Generic OPDS fallback for Ubooquity/COPS/etc. — deferred; add only if demand materializes
- Tachidesk/Suwayomi support — fundamentally different model (source aggregator, not library), out of scope

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Min SDK:** 26 (Android 8.0); **Target SDK:** 35
- **Architecture:** MVVM with `data` / `domain` / `ui` layering, single-activity
- **DI:** Hilt
- **Networking:** Retrofit + OkHttp + Kotlinx Serialization
- **Image loading:** Coil 3 (shares OkHttp client for auth)
- **EPUB rendering:** Readium Kotlin Toolkit
- **CBR/RAR support:** junrar
- **PDF rendering:** Android `PdfRenderer` (built-in)
- **Local storage:** Room (structured), DataStore (preferences), EncryptedSharedPreferences (credentials)
- **Background work:** WorkManager (downloads, progress sync, update checks)
- **Navigation:** Compose Navigation

## Module / Package Layout

Single Gradle module for v1 (solo project, faster iteration). Can split later.

```
com.koma.client/
├── data/
│   ├── server/
│   │   ├── komga/      (KomgaApi, KomgaMediaServer, DTOs, mappers)
│   │   ├── kavita/     (KavitaApi, KavitaMediaServer, DTOs, mappers, JWT auth)
│   │   └── calibreweb/ (CalibreWebApi, CalibreWebMediaServer, DTOs, mappers, session/basic auth)
│   ├── db/          (Room entities, DAOs, database)
│   ├── repo/        (repository implementations — backend-agnostic)
│   └── auth/        (credential storage, per-backend authenticators)
├── domain/
│   ├── model/       (unified domain models)
│   ├── server/      (MediaServer interface, MediaServerType enum)
│   ├── repo/        (repository interfaces)
│   └── usecase/     (use cases)
├── ui/
│   ├── theme/       (Koma navy palette + dynamic color)
│   ├── auth/        (server add/edit, login)
│   ├── home/        (recently added, on-deck, continue reading)
│   ├── library/     (libraries → series → series detail)
│   ├── search/
│   ├── reader/
│   │   ├── image/   (CBZ, CBR, PDF, manga)
│   │   ├── epub/    (Readium-backed)
│   │   └── common/  (bookmarks, progress sync, shared UI)
│   ├── downloads/
│   ├── settings/
│   └── update/      (in-app updater UI)
├── work/            (WorkManager jobs)
└── di/              (Hilt modules)
```

## Features (v1)

### Authentication & Servers
- Multi-server support; any mix of Komga, Kavita, and Calibre-Web servers
- Add server flow: pick backend type (Komga / Kavita / Calibre-Web) → base URL → credentials → optional display name
- Optional **auto-detect** on add: probe each backend's identifying endpoint to pre-select the type
  - Komga: `GET /api/v1/users/me`
  - Kavita: `GET /api/Plugin/version`
  - Calibre-Web: `GET /opds` (OPDS catalog root, returned by Calibre-Web)
- Credentials stored via EncryptedSharedPreferences
- **Komga auth:** HTTP Basic via OkHttp authenticator
- **Kavita auth:** JWT bearer token — login endpoint exchanges credentials for token, interceptor attaches `Authorization: Bearer`, refresh flow on 401
- **Calibre-Web auth:** HTTP Basic (default when Calibre-Web is configured for it) or session-cookie login via `POST /login` for setups that disable Basic — Koma tries Basic first and falls back to session auth
- 401 response triggers re-login prompt (all backends)
- Server switcher in navigation drawer, shows backend type badge

### Browsing
- **Home:** recently added, recently read, on-deck (continue reading), keep-reading carousels
- **Libraries:** grid of libraries on the active server
- **Series browser:** grid/list toggle; filters (all / unread / in-progress / completed); sort (title, date added, last read)
- **Series detail:** metadata, description, book list, "read next" shortcut, collection/readlist membership (read-only)
- **Search:** global search (series + books) with filters; last query remembered
- **Terminology unification:**
  - Komga books map 1:1 to the unified `Book` model
  - Kavita's Volume/Chapter hierarchy is flattened into `Book`; non-trivial volumes render as section headers on the series detail screen
  - Calibre-Web is **flat** — no series concept by default. Koma surfaces Calibre "books" as a synthetic `Library → Book` flow (each library shows books directly, series detail is skipped when empty). Calibre "series" metadata (when present) is used to optionally group books under virtual Series entries.
- **Content type expectations per backend:**
  - Komga & Kavita: primarily images (CBZ/CBR/PDF/manga) with some EPUB
  - Calibre-Web: primarily EPUB and PDF, occasional CBZ

### Downloads (Offline)
- WorkManager job per book, app-private storage
- Room tracks state (queued / downloading / complete / failed) with progress
- Downloaded books open from local file (reader bypasses network)
- Manual delete + optional auto-cleanup (LRU by last-read)

### Image Reader (CBZ, CBR, PDF, manga)
- **Fit modes:** width / height / screen / original
- **Reading direction:** LTR / RTL / vertical (webtoon)
- **Page layout:** single / double (auto-pair)
- **Crop whitespace** toggle
- **Background color picker**
- **Custom tap zones** (editable prev/next/menu regions)
- **Pinch zoom with per-book memory** — last zoom level and pan position restored on reopen
- **Preload N pages ahead** (configurable)
- **Brightness slider overlay**
- **Volume-key page turn**
- **Keep-screen-on** toggle

### EPUB Reader (Readium)
- **Font family picker:** system + bundled (Inter, Lora, Merriweather, OpenDyslexic)
- **Font size, line height, margin width** sliders
- **Theme:** light / sepia / dark / Koma navy
- **Justified text** toggle
- **Publisher font override** toggle
- Standard Readium locator-based progress

### Bookmarks
- Per-book, stored locally (Room)
- Image books: bookmark by page number
- EPUB: bookmark by Readium locator
- Optional user note per bookmark
- Bookmark list screen per book; jump-to action

### Read Progress Sync
- Optimistic local write on page change (debounced)
- WorkManager sync job pushes to Komga (`/books/{id}/read-progress`)
- Retry with exponential backoff
- `dirty` flag cleared on success

### Settings
- Theme (dynamic color toggle, dark/light/system)
- Default reader preferences (image + EPUB)
- Server management (add/edit/remove/switch active)
- Cache management (clear thumbnails, clear downloads)
- About (version, GitHub link, license)

### In-App Updater
- Daily WorkManager check against GitHub Releases API
- Notification when newer version available
- One-tap download (to app cache) and install via `PackageInstaller`
- Requires `REQUEST_INSTALL_PACKAGES` permission (sideload-friendly)
- Can be disabled in settings

## Theme

- **Signature:** Koma navy (deep midnight blue, e.g. `#0B1E3F` primary, `#1A3A6C` secondary, accent `#4A90E2`)
- **Material You:** on Android 12+, dynamic color from wallpaper is the default when enabled
- **Fallback:** Koma navy on older devices or when dynamic color is disabled
- **Light mode:** navy-tinted light palette (brand preserved)
- **Reader themes:** independent of app theme (light / sepia / dark / Koma navy for EPUB; background picker for image reader)

## Domain Abstraction: MediaServer

A single interface abstracts both backends. All repositories, use cases, and UI depend on this — not on Komga or Kavita directly.

```kotlin
enum class MediaServerType { KOMGA, KAVITA, CALIBRE_WEB }

interface MediaServer {
    val type: MediaServerType
    val id: String           // Koma-local server id
    suspend fun authenticate(): Result<Unit>
    fun libraries(): Flow<List<Library>>
    fun series(libraryId: String, filter: SeriesFilter): Flow<PagingData<Series>>
    suspend fun seriesDetail(id: String): SeriesDetail
    fun books(seriesId: String): Flow<List<Book>>
    suspend fun book(id: String): Book
    suspend fun pageUrl(bookId: String, page: Int): String
    suspend fun fileUrl(bookId: String): String          // EPUB / full file
    suspend fun thumbnailUrl(id: String, kind: ThumbKind): String
    suspend fun updateProgress(bookId: String, progress: ReadProgress)
    suspend fun search(query: String, filter: SearchFilter): SearchResults
}
```

Concrete implementations: `KomgaMediaServer`, `KavitaMediaServer`, `CalibreWebMediaServer`. Each owns its own Retrofit service, DTOs, and mappers into the unified domain models. IDs in the unified model are strings (Kavita and Calibre int IDs are stringified). The active-server registry resolves a `MediaServer` from a `servers` row based on `type`.

**Capability flags.** Because backends differ in what they natively support, `MediaServer` exposes a `capabilities: ServerCapabilities` value:

```kotlin
data class ServerCapabilities(
    val serverProgressSync: Boolean,   // Komga, Kavita: true; Calibre-Web: false
    val serverBookmarks: Boolean,      // v1: all false (Koma stores bookmarks locally)
    val nativeSearch: Boolean,         // Komga, Kavita: true; Calibre-Web: true (but less rich)
    val collections: Boolean,          // read-only where supported
    val readlists: Boolean,
    val nativeSeries: Boolean          // Komga, Kavita: true; Calibre-Web: false (virtual)
)
```

UI gracefully hides or disables features that a given server doesn't support (e.g. Calibre-Web servers don't show the "server-side progress" indicator, and read progress is stored locally only for those books).

Repositories depend only on `MediaServer` + Room, and combine the two into a single source of truth that the UI observes.

## Data Model

### Room Schema

```
servers(
  id PK, type TEXT,            -- 'KOMGA' | 'KAVITA' | 'CALIBRE_WEB'
  name, baseUrl,
  username, encPassword,       -- Komga/Calibre-Web: basic auth; Kavita: used for login to get JWT
  encJwtToken NULLABLE,        -- Kavita only, refreshable
  encSessionCookie NULLABLE,   -- Calibre-Web session-auth fallback
  isActive, lastSyncAt
)

cached_libraries(id PK, serverId FK, name, unreadCount, ...)

cached_series(
  id PK, serverId FK, libraryId FK,
  title, bookCount, unreadCount, thumbUrl, ...
)

cached_books(
  id PK, serverId FK, seriesId FK,
  title, number, pageCount, mediaType, sizeBytes, ...
)

read_progress(
  bookId PK, page, completed, locator, updatedAt,
  dirty BOOL  -- needs sync to server
)

bookmarks(
  id PK, bookId FK, page NULLABLE, locator NULLABLE,
  note, createdAt
)

downloads(
  bookId PK, state, bytesDownloaded, totalBytes,
  filePath, error, updatedAt
)

reader_state(
  bookId PK, zoomLevel, panX, panY, lastFitMode
)
```

### Data Flow

- Repositories expose `Flow`s backed by Room (single source of truth)
- Network fetches write to Room; UI observes Room
- Read progress: optimistic local write → WorkManager sync → clear `dirty`
- Reader state (zoom/pan) saved on reader close and periodically

## Backend APIs

### Komga (REST v1, `/api/v1/...`, HTTP Basic)

- `GET /libraries`
- `GET /series` (with filters and pagination)
- `GET /series/{id}`, `GET /series/{id}/books`
- `GET /books/{id}`, `GET /books/{id}/pages/{n}`, `GET /books/{id}/file`, `GET /books/{id}/thumbnail`
- `PATCH /books/{id}/read-progress`
- `GET /books/search`

`openapi.json` from the KMReader repo is the canonical reference for Komga request/response shapes.

### Kavita (REST, `/api/...`, JWT bearer)

- `POST /api/Account/login` — exchange credentials for JWT
- `POST /api/Account/refresh-token` — refresh on 401
- `GET /api/Library` — libraries
- `GET /api/Series`, `GET /api/Series/{id}`, `GET /api/Series/volumes?seriesId=`
- `GET /api/Series/chapter?chapterId=`
- `GET /api/Reader/image?chapterId=&page=` — image pages
- `GET /api/Reader/file?chapterId=` — EPUB/full file
- `GET /api/Image/series-cover?seriesId=`, `GET /api/Image/chapter-cover?chapterId=`
- `POST /api/Reader/progress` — progress update
- `GET /api/Search/search`

Kavita's official Swagger at `{host}/swagger` is the canonical reference.

### Calibre-Web (REST + OPDS, HTTP Basic or session cookie)

Calibre-Web exposes two surfaces; Koma uses the combination that gives the cleanest experience:

- **OPDS 2 / 1.2 feed** at `/opds` — library listing, books, categories, navigation; used for browsing and metadata
- **REST-ish JSON endpoints** used by the Calibre-Web web UI (e.g. `/ajax/book/{id}`, `/ajax/listbooks`) — used for richer book details and filtering
- **File download:** `GET /opds/download/{book_id}/{format}/` — serves EPUB/PDF/etc.
- **Cover:** `GET /cover/{book_id}`
- **Auth:** `POST /login` for session auth fallback; HTTP Basic otherwise
- **Read progress:** Calibre-Web does **not** offer a stable API for per-page progress sync. Koma stores progress **locally only** for Calibre-Web books (capability flag `serverProgressSync = false`). This is a known limitation clearly communicated in the UI.

Calibre-Web's source at `github.com/janeczku/calibre-web` is the canonical reference since there is no formal OpenAPI spec.

### Mapping Notes
- Kavita IDs are integers → stringified in the unified model
- A Kavita `Chapter` (and in some cases `Volume`) maps to a unified `Book`
- Calibre-Web book IDs are integers → stringified
- Calibre-Web books map to `Book`; Calibre "series" (when present on a book) optionally group under virtual `Series` entries; otherwise libraries show books directly
- Progress: Komga per book/page, Kavita per chapter/page → both sync to server; Calibre-Web → local-only
- All backends map cleanly to `ReadProgress(bookId, page, completed, locator?)` at the domain level

## Error Handling

- **Network errors:** cached data from Room shown with offline banner; retry affordance
- **Auth errors (401):** re-login dialog, preserves navigation state
- **Server errors (5xx):** toast + retry; log to in-app debug log
- **Reader errors:** per-book error screen with "report" action (opens GitHub issue template)
- **Download failures:** visible in downloads screen with retry

## Testing Strategy

- **Unit tests:** repositories, use cases, view models (with fakes)
- **Room tests:** DAO tests on in-memory database
- **API tests:** MockWebServer for Retrofit services
- **UI tests:** Compose test for critical flows (login, open book, bookmark)
- **Screenshot tests:** (stretch) Paparazzi for theme verification

## Distribution

- GitHub Releases with signed APK (v1 keystore, Koma-only)
- Release notes in `CHANGELOG.md`
- In-app updater polls GitHub Releases API daily
- README with sideload instructions

## Build Sequence (high level)

1. Project scaffolding (Gradle, Hilt, Compose, theme)
2. `MediaServer` interface + `ServerCapabilities` + unified domain models
3. Komga backend implementation (`KomgaMediaServer`, Basic auth, DTOs, mappers)
4. Kavita backend implementation (`KavitaMediaServer`, JWT auth + refresh, DTOs, mappers)
5. Calibre-Web backend implementation (`CalibreWebMediaServer`, Basic/session auth, OPDS + ajax, DTOs, mappers)
6. Room schema + backend-agnostic repositories
7. Server add/edit flow (with backend picker + auto-detect for all three)
8. Library/series/book browsing (online only, all backends, capability-aware UI)
9. Image reader (basic) + progress sync (skipped for Calibre-Web backends)
10. EPUB reader (Readium integration)
11. Reader customization (settings, tap zones, zoom memory, etc.)
12. Bookmarks
13. Downloads (offline reading, all backends)
14. Search + filters
15. Home screen (carousels)
16. In-app updater
17. Polish, screenshots, first release

Detailed implementation plan to follow in a separate document via `writing-plans`.
