# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK (minified + shrunk)
./gradlew build                # Full build
./gradlew clean                # Clean build outputs
```

This is a single-module Gradle project (`:app`). There are no tests configured.

## Project Overview

Android music player app for local audio files. Kotlin, Jetpack Compose, targets API 26-34.

**Key tech stack**: Media3/ExoPlayer (playback), Room (database), Hilt (DI), Compose Navigation, DataStore (preferences), Coil (images), jAudioTagger (metadata).

Dependency versions are centralized in `gradle/libs.versions.toml`.

## Architecture

Clean Architecture with three layers, all under `com.musicplayer.app`:

- **`domain/`** - Models (`Song`, `Album`, `Artist`, `Folder`, `Playlist`, `Genre`, `Year`), repository interfaces, and use cases. No Android framework dependencies.
- **`data/`** - Repository implementations, Room database (`MusicDatabase` with entities + DAOs), and `MediaScanner` which discovers audio files via MediaStore + custom folder scanning.
- **`ui/`** - Compose screens organized by feature under `ui/screens/`, reusable components in `ui/components/`, navigation in `ui/navigation/`, Material 3 theming in `ui/theme/`.

### Playback System (`player/`)

Three singletons manage playback, all injected via Hilt:

- **`PlaybackController`** - Controls ExoPlayer through Media3 `MediaController`. Exposes `PlaybackState` as `StateFlow` (current song, position, duration, playing/paused, shuffle, repeat mode).
- **`QueueManager`** - Manages the playback queue and current index. Handles shuffle by maintaining original order separately.
- **`PlaybackService`** - `MediaSessionService` that runs ExoPlayer in a foreground service. Handles audio focus and initializes `EqualizerManager`.
- **`EqualizerManager`** (`player/audio/`) - Wraps Android AudioFX Equalizer and BassBoost. Persists settings to DataStore.

### Dependency Injection (`di/`)

Three Hilt modules, all `@InstallIn(SingletonComponent::class)`:
- `AppModule` - DataStore instance
- `DatabaseModule` - Room database and DAOs
- `RepositoryModule` - Repository interface bindings

All ViewModels use `@HiltViewModel`. All screens obtain ViewModels via `hiltViewModel()`.

### Navigation (`ui/navigation/`)

`Screen` sealed class defines all routes. `NavGraph.kt` wires them up. Bottom navigation has 4 tabs: Library, Equalizer, Search, Queue. Detail screens (album, artist, genre, year) take path parameters. NowPlaying and Settings are modal screens that hide the bottom nav bar.

### Database

Room database (`MusicDatabase`, version 2) with 4 entities: `PlaylistEntity`, `PlaylistSongEntity` (join table), `FavoriteEntity`, `SearchHistoryEntity`. Migration v1->v2 added `search_history`.

### Data Flow Pattern

Repository interfaces live in `domain/repository/`, implementations in `data/repository/`. ViewModels consume repositories via constructor injection and expose `StateFlow` to Compose UI. Reactive chains use `map`, `flatMapLatest`, `combine` on Flows.
