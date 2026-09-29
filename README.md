# MediaDeck

MediaDeck is an Android media library for browsing local and SMB folders. It includes a comic reader, photo gallery, and video player.

[![Download APK](https://img.shields.io/badge/Download-APK-3DDC84?logo=android&logoColor=white)](https://github.com/okkysatria/MediaDeck/releases/latest/download/MediaDeck.apk)

## Features

- **Comics:** Read image folders and CBZ/ZIP archives in vertical, single-page, or double-page modes. Reading progress and page navigation are supported.
- **Gallery:** Browse photos and videos in grid or title-only layouts, group items by folder, and filter or sort the library. Full-resolution media opens in the viewer.
- **Movies:** Play local or SMB-hosted video, with playback history, resume, subtitles, picture-in-picture, and gesture controls.
- **SMB:** Browse network shares, scan media, and stream video without downloading the whole file first.
- **Library processing:** Scan progress is reported through a foreground service; thumbnails are cached for browsing.

## Project Structure

```text
app/src/main/java/com/mediadeck/app/
|-- data/       Room entities, DAOs, and repository
|   |-- comic/
|   |-- gallery/
|   |-- movie/
|   `-- settings/
|-- di/         Database and repository dependency-injection setup
|-- service/    Foreground media-scanning service
|-- ui/
|   |-- components/
|   |-- navigation/
|   |-- screens/
|   |   `-- settings/
|   `-- theme/
|-- util/
|   |-- cache/  Thumbnail and media cache helpers
|   |-- i18n/   Localization
|   |-- media/  Media identity, thumbnail, and media helpers
|   |-- scan/   Scan and media-processing pipeline
|   |-- smb/    SMB connection, scanner, and content provider
|   `-- zip/    ZIP/CBZ content provider and page reader
`-- viewmodel/  UI state and application actions

app/src/test/        Local unit and Robolectric tests
app/src/androidTest/ Instrumented Android tests
gradle/              Gradle wrapper and dependency version catalog
```

## Main Dependencies

Versions are managed in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Area | Dependency | Version |
| --- | --- | --- |
| UI | Jetpack Compose (BOM) | 2026.09.00 |
| Language | Kotlin | 2.4.20 |
| Navigation | Navigation Compose | 2.10.2 |
| Database | Room | 2.8.5 |
| Video playback | AndroidX Media3 | 1.11.1 |
| Image loading | Coil 3 | 3.6.3 |
| SMB | `org.codelibs:jcifs` | 3.0.3 |
| Dependency injection | Hilt | 2.60.1 |
| Async work | Kotlin Coroutines | 1.11.0 |
| HTTP and JSON | OkHttp, Retrofit, Moshi | See version catalog |

The SMB client is the CodeLibs `jcifs` artifact; this project does not depend on SMBJ.

## Requirements

- Android Studio with Android SDK 37 installed
- JDK 17 or newer
- Minimum Android version: Android 7.0 (API 24)
- Target Android version: API 37
- Gradle Wrapper: 9.7.1
- Android Gradle Plugin: 9.4.1

## Build and Test

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

Run local unit and Robolectric tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run instrumented tests on a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/MediaDeck.apk`.

## Release Signing

Release builds require a private upload keystore. The signing configuration reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` from the environment. The key alias is `upload`. Keep the keystore and passwords private; do not commit them to the repository.

```bash
./gradlew :app:assembleRelease
```

## Dependency Updates

Update dependency versions in `gradle/libs.versions.toml`, then run the build and tests above. Keep plugin versions and library versions aligned with their compatibility requirements.
