# Playback System

## Architecture

```
UI (Screens/ViewModels)
        │
        ▼
PlaybackController (@Singleton)  ←→  QueueManager (@Singleton)
        │
        ▼ (MediaController)
PlaybackService (MediaSessionService)
        │
        ▼
    ExoPlayer  →  EqualizerManager
```

## PlaybackController (`player/PlaybackController.kt`)

Central API for all playback operations. Singleton injected via Hilt.

### Key State
- `playbackState: StateFlow<PlaybackState>` — current song, isPlaying, position, duration, shuffle, repeat mode, progress

### Key Methods
- `playSongs(songs: List<Song>, startIndex: Int)` — replaces queue and starts playback
- `playAtIndex(index: Int)` — play specific queue position
- `togglePlayPause()` — handles Player.STATE_ENDED by seeking to start
- `skipToNext()` / `skipToPrevious()`
- `seekToFraction(fraction: Float)` — seek by 0.0–1.0 progress
- `toggleShuffle()` / `toggleRepeatMode()`

### State Resilience
- **`onEvents` safety net**: After every batch of player events, verifies `currentSong` and `isPlaying` match the actual player state. Catches edge cases during Bluetooth disconnect, audio-source changes, or service reconnection where individual callbacks may be missed.
- **Service reconnection sync**: When the MediaController reconnects to a fresh service (`mediaItemCount == 0`), the queue is reloaded and `_playbackState` is explicitly synced to prevent the current song from disappearing from the UI.

### Persistence
- Saves/restores last queue + position to DataStore
- `CONTINUE_TO_NEXT_FOLDER` DataStore key for end-of-queue folder continuation
- Injects `MusicRepository` for folder-continuation feature

## QueueManager (`player/QueueManager.kt`)

Manages the song queue independently from the player.

### Key State
- `queue: StateFlow<List<Song>>` — current queue
- `currentIndex: StateFlow<Int>` — current position
- `currentSong: StateFlow<Song?>` — derived from queue + index
- `shuffleEnabled: StateFlow<Boolean>`

### Shuffle Behavior
Maintains both original and shuffled queue. When shuffle toggled on, shuffles remaining songs. When toggled off, restores original order at current song position.

## PlaybackService (`player/service/PlaybackService.kt`)

Media3 `MediaSessionService` running ExoPlayer in foreground.

### Responsibilities
- Creates and manages ExoPlayer instance
- Sets up MediaSession for system media controls
- Manages audio focus
- Creates media notification
- Initializes EqualizerManager with audio session ID
- Handles Bluetooth events via BluetoothReceiver

## EqualizerManager (`player/audio/EqualizerManager.kt`)

Wraps Android AudioFX `Equalizer` and `BassBoost`.

- Initializes with ExoPlayer's audio session ID
- Persists band levels and bass boost to DataStore
- Restores settings on initialization

## PlaybackState (`player/PlaybackState.kt`)

```kotlin
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val progress: Float = 0f
)
```

## RepeatMode (`player/RepeatMode.kt`)

```kotlin
enum class RepeatMode { OFF, ALL, ONE }
// next(): OFF → ALL → ONE → OFF
```

## Common Playback Patterns

### Play a list of songs from a screen
```kotlin
// In any ViewModel
fun playSong(song: Song, allSongs: List<Song>) {
    val index = allSongs.indexOf(song)
    playbackController.playSongs(allSongs, index)
}
```

### Play all songs with shuffle
```kotlin
playbackController.playSongs(songs, startIndex = 0)
playbackController.toggleShuffle() // if not already on
```
