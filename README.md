# Music Player

A modern Android music player for local audio files, built with Kotlin and Jetpack Compose.

## Screenshots

<p align="center">
  <img src="docs/libraryPage.jpg" width="200" alt="Library">
  <img src="docs/songListPage.jpg" width="200" alt="Song List">
  <img src="docs/currentlyPlayedSongPage.jpg" width="200" alt="Now Playing">
</p>
<p align="center">
  <img src="docs/equalizerPage.jpg" width="200" alt="Equalizer">
  <img src="docs/searchPage.jpg" width="200" alt="Search">
  <img src="docs/songQueuePage.jpg" width="200" alt="Queue">
</p>

## Features

- **Library** - Browse by songs, folders, albums, artists, album artists, composers, genres and years
- **Folder hierarchy** - Navigate your music folders with a nested folder view
- **Now Playing** - Album art carousel, seek bar, shuffle and repeat controls
- **Equalizer** - 5-band EQ with preamp, tone and limiter controls
- **Queue** - View and manage your playback queue with drag-to-reorder
- **Search** - Search across all categories with search history
- **Playlists & Favorites** - Create playlists and mark favorite tracks
- **Notification controls** - Media controls with album art in the notification shade
- **Bluetooth / headset support** - Auto-resume on Bluetooth or headset connection
- **Persistent queue** - Automatically restores your queue on app restart

## Tech Stack

Kotlin, Jetpack Compose, Material 3, Media3 / ExoPlayer, Room, Hilt, Coil, DataStore, jAudioTagger

**Min SDK**: 26 (Android 8) | **Target SDK**: 34 (Android 14)

## Build

```bash
./gradlew assembleDebug    # debug APK
./gradlew assembleRelease  # release APK (minified + shrunk)
```

Output APKs are in `app/build/outputs/apk/`.

For architecture details, see [Architecture.md](Architecture.md).
