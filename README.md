# Koma

A modern Android client for self-hosted reading servers. Browse, read, and manage your manga, comics, and ebook libraries from [Komga](https://komga.org), [Kavita](https://www.kavitareader.com), and [Calibre-Web](https://github.com/janeczku/calibre-web) — all in one app.

Inspired by [KMReader](https://github.com/everpcpc/KMReader) (iOS), rebuilt natively for Android with Material 3 and Jetpack Compose.

## Features

### Multi-Server Support
- Connect to **Komga**, **Kavita**, and **Calibre-Web** servers
- Manage multiple servers — add, switch, edit, or remove
- Auto-prepends `https://` so you don't have to type it
- Default server auto-login on app launch

### Dashboard
- **On Deck** — continue reading where you left off
- **Recently Released / Added Books** — newest content
- **Recently Added / Updated Series** — library changes at a glance
- **Recently Read** — your reading history
- Library filter dropdown to scope content to a specific library
- Pull-to-refresh

### Search & Filters
- Full-text search across your library
- Advanced filter sheet: sort by, publication status, read status, genres, tags, publishers/writers
- Collapsible filter sections for clean UX

### Reading
- **Image Reader** (CBZ, CBR, PDF, manga)
  - Page-width fit with swipe navigation (LTR, RTL, or vertical)
  - Page slider for quick jumping
  - Volume key page turn
  - Brightness slider
  - Reader settings (fit mode, direction, page layout)
- **EPUB Reader**
  - WebView-based with full customization
  - Font family, size, line height, margin control
  - Themes: Light, Sepia, Dark, Koma Navy
  - Justified text toggle
  - Chapter navigation with table of contents
- **Bookmarks** — save your place with optional notes, per-book bookmark list
- **Read progress sync** — automatically syncs to Komga and Kavita servers

### Series Detail
- Cover art, metadata, genres, tags, authors/artists
- Publication status (Ongoing, Completed, Hiatus, Abandoned)
- Chapter count with read/total progress
- Expandable summary
- Start Reading / Continue Reading button
- Multi-select books: mark as read, download
- Sort chapters ascending/descending

### Downloads
- Download books for offline reading
- Progress tracking per download
- Manage downloads: view active, queued, complete, and failed
- Delete individual or all downloads

### Settings
- **Theme**: System, Light, or Dark mode
- **Dynamic Color**: Material You support on Android 12+
- **Reader preferences**: defaults for image and EPUB readers
- **Cache management**: clear image cache
- **Auto-update check**: checks GitHub Releases for new versions

## Screenshots

*Coming soon*

## Tech Stack

- **Language:** Kotlin 2.0
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + Clean-ish layering
- **DI:** Hilt
- **Networking:** Retrofit + OkHttp + Kotlinx Serialization
- **Image Loading:** Coil 3
- **Local Storage:** Room + DataStore + EncryptedSharedPreferences
- **Background Work:** WorkManager
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35

## Installation (Sideloading)

Koma is distributed as a signed APK via GitHub Releases. It is not on the Play Store.

### Prerequisites

- An Android device or emulator running **Android 8.0 (Oreo)** or later
- A self-hosted **Komga**, **Kavita**, or **Calibre-Web** server accessible from your device

### Steps

1. **Download the APK**

   Go to the [Releases](../../releases) page and download the latest `koma-v*.apk` file.

2. **Allow installation from unknown sources**

   On your Android device:
   - Go to **Settings > Apps > Special app access > Install unknown apps**
   - Select your browser (or file manager) and enable **Allow from this source**
   - On older Android versions: **Settings > Security > Unknown sources**

3. **Install the APK**

   - Open the downloaded `.apk` file
   - Tap **Install** when prompted
   - If Google Play Protect warns you, tap **Install anyway** (the app is open source and safe)

4. **Launch Koma**

   - Open the app from your app drawer
   - Tap **Add Server**
   - Select your server type (Komga, Kavita, or Calibre-Web)
   - Enter your server URL (e.g., `komga.example.com` — https is added automatically)
   - Enter your username and password
   - Tap **Test Connection** to verify, then **Save**

5. **Start reading!**

### Updating

Koma checks for updates automatically (configurable in Settings). When a new version is available, you'll be notified. Download the new APK and install it over the existing one — your data is preserved.

### Building from Source

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/Koma.git
cd Koma

# Build the debug APK
./gradlew :app:assembleDebug

# The APK is at: app/build/outputs/apk/debug/app-debug.apk

# Or build the release APK (unsigned)
./gradlew :app:assembleRelease
```

**Requirements:**
- JDK 17+ (Android Studio bundles one)
- Android SDK with Platform 35 and Build Tools 35.0.0

## Server Setup

### Komga
- [Komga installation guide](https://komga.org/docs/installation/)
- Default auth: HTTP Basic (username + password)
- Full feature support in Koma

### Kavita
- [Kavita installation guide](https://wiki.kavitareader.com/en/install)
- Auth: JWT bearer token (Koma handles this automatically)
- Demo server: `demo.kavitareader.com` / `demouser` / `Demouser64`
- Full feature support in Koma

### Calibre-Web
- [Calibre-Web installation guide](https://github.com/janeczku/calibre-web/wiki)
- Auth: HTTP Basic (enable in Calibre-Web admin settings)
- Note: Read progress is stored locally only (Calibre-Web has no progress sync API)
- Books are grouped into virtual series based on Calibre metadata

## Roadmap

- [ ] OPDS generic server support
- [ ] Kaizoku / Lazy Librarian integration for requesting new content
- [ ] Reading statistics and history
- [ ] Improved offline support (local CBZ page extraction)
- [ ] Play Store release (based on demand)

## License

MIT

## Credits

- Inspired by [KMReader](https://github.com/everpcpc/KMReader) by everpcpc
- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose), [Hilt](https://dagger.dev/hilt/), [Coil](https://coil-kt.github.io/coil/), [Retrofit](https://square.github.io/retrofit/), [Room](https://developer.android.com/training/data-storage/room)
- Icons from [SVG Repo](https://www.svgrepo.com/)
