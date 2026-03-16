# Music Player

An Android music player for local audio files.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Playback**: Media3 / ExoPlayer
- **Database**: Room
- **DI**: Hilt
- **Images**: Coil
- **Metadata**: jAudioTagger
- **Preferences**: DataStore
- **Min SDK**: 26 (Android 8) — **Target SDK**: 34 (Android 14)

## Features

- Browse music by Songs, Albums, Artists, Genres, Years, Folders, Playlists
- Now Playing screen with album art, seek bar, shuffle & repeat
- Playback notification with media controls and close button
- Equalizer with band sliders and bass boost
- Persistent queue with auto-restore on app restart
- Search with history
- Favorites
- Bluetooth / headset auto-resume

## Build

```bash
./gradlew assembleDebug    # debug APK
./gradlew assembleRelease  # release APK (minified + shrunk)
./gradlew clean            # clean build outputs
```

Output APKs are in `app/build/outputs/apk/`.

## Project Structure

```
app/src/main/java/com/musicplayer/app/
├── data/          # Room DB, DAOs, repository implementations, MediaScanner
├── di/            # Hilt modules
├── domain/        # Models, repository interfaces, use cases
├── player/        # PlaybackController, QueueManager, PlaybackService, EqualizerManager
└── ui/
    ├── components/ # MiniPlayer, SongItem, AlbumArtImage, …
    ├── navigation/ # NavGraph, Screen routes
    ├── screens/    # One package per feature screen
    └── theme/      # Material 3 color, type, theme
```
