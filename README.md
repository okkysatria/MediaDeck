# MediaDeck

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?logo=android&logoColor=white)](https://github.com/okkysatria/MediaDeck/releases/download/v1.0/MediaDeck.apk)

Android application for managing and viewing local and network (SMB) media. Supports comics, video streaming, and photo galleries.

## Download

**Latest APK:** [MediaDeck.apk (v1.0)](https://github.com/okkysatria/MediaDeck/releases/download/v1.0/MediaDeck.apk)

## Features

### Comics
- Vertical (Webtoon), single, and double page reading modes.
- Sidebar navigation with thumbnail previews.
- Natural alphanumeric file sorting (e.g., 1, 2, 10 instead of 1, 10, 2).
- Minimalist overlay UI.
- Reads folders of images and `.cbz`/`.zip` archives directly (no extraction needed).

### Video
- Direct SMB streaming via custom Media3 DataSource (no pre-downloading).
- Gesture controls for volume, brightness, and seeking.
- Picture-in-Picture (PiP) support.
- Support for external `.srt` subtitles with persistent SAF permissions.
- Playback history and automatic resume.

### Gallery
- Staggered masonry grid layout.
- Automatic metadata extraction (Titles, Tags, IDs) from filenames.
- Intelligent bitmap downsampling to prevent OOM on large libraries.
- View filters by media type, tags, and favorites.
- Low-res sharp thumbnails in grid; tap to open full-resolution original in viewer.

## Tech Stack
- **Navigation**: Jetpack Navigation Compose (Adaptive)
- **UI**: Jetpack Compose (Material 3)
- **Video**: Media3 ExoPlayer
- **Database**: Room
- **Network**: JCIFS-NG (Samba)
- **Image Loading**: Coil
- **DI**: Hilt
- **Language**: Kotlin 2.4 + Coroutines/Flow

## Project Structure
- `data/`
  - `comic/`, `gallery/`, `movie/`: Entity & DAO definitions.
  - `settings/`: App configuration entities.
  - `AppDatabase.kt` & `AppRepository.kt`: Centralized data management.
- `di/`: Hilt dependency injection modules (Database & Network).
- `service/`: Foreground services (e.g., Scanner).
- `ui/`
  - `components/`: Reusable Compose elements (Dialogs, Cards, Media box).
  - `navigation/`: App routing and nested graphs.
  - `screens/`: Application screens (Comics, Gallery, Video, Settings).
  - `theme/`: Compose theming (Colors, Typography).
- `util/`
  - `cache/`: Disk cache & entry locking.
  - `i18n/`: String translations and localization.
  - `media/`: Helpers for thumbnail extraction and mime-type processing.
  - `scan/`: Content processing algorithms (Local & Network).
  - `smb/`: SMB connection pool, JCIFS-NG adapters, and stream proxies.
  - `zip/`: CBZ/ZIP extraction content providers.
- `viewmodel/`: StateFlow logic connecting UI and Repository.

## Development
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 37 (Android 13+)
- Compile SDK: 37
- Build System: Gradle with Kotlin DSL (AGP 9.3)
- Required IDE: Android Studio (2024.2.1+ / JDK 17+)
