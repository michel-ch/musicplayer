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
- **`onEvents` safety net**: After every batch of player events, verifies `currentSong` and `isPlaying` match the actual player state. If the in-memory queue was lost (e.g. service kill after BT-induced pause) but `currentSong` is still set, only `isPlaying` is reconciled; if `currentSong` is also null the listener falls back to `restoreSnapshotSync()` before reconciling. The chosen index also tolerates a stale `currentMediaItemIndex` by clamping to `queueManager.currentIndex`.
- **Service reconnection sync**: When the MediaController reconnects to a fresh service (`mediaItemCount == 0`), the queue is reloaded and `_playbackState` is explicitly synced to prevent the current song from disappearing from the UI. If the reconnect lands with an empty `queueManager.queue` *and* a null `currentSong` (process didn't fully restart but state was lost), the controller calls `restoreSnapshotSync()` to rehydrate from the SharedPreferences snapshot before loading items into the new service.
- **`onMediaItemTransition` null guard**: Service teardown, focus loss, and similar events can fire a transition with `mediaItem == null` under a reason other than `PLAYLIST_CHANGED`; the listener explicitly ignores these so `currentSong` cannot be clobbered.

### Persistence
- Saves last queue + index + source route to DataStore (durable) **and** to a `playback_snapshot` SharedPreferences file.
- `restoreSnapshotSync()` runs first in `init`, synchronously reading the SharedPreferences snapshot and seeding `_playbackState` + `QueueManager` before `connectToService()` starts. This closes the process-restart gap where the MiniPlayer would briefly hide while the async DataStore restore was in flight.
- `CONTINUE_TO_NEXT_FOLDER` DataStore key for end-of-queue folder continuation.
- Injects `MusicRepository` for folder-continuation feature.

### Deletion
- `onSongDeleted(songId)` removes the song from the queue and MediaController, advances to the next song when the deleted entry was current, and clears `_playbackState` + snapshot + DataStore keys when the last song is removed.
- The UI side of deletion is centralised in **`SongDeletionHandler`** (see file map), not in PlaybackController.

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

### Lifecycle: `onTaskRemoved`
The service only calls `stopSelf()` when the queue is empty (`mediaItemCount == 0`). Tearing down on `!playWhenReady` would also kill the session whenever ExoPlayer auto-paused via `setHandleAudioBecomingNoisy` (e.g. a Bluetooth disconnect), and the user swiping the app away would then orphan the MiniPlayer's backing state. Keeping the service alive while paused-with-queue lets the user resume playback after a BT round-trip without losing context.

## App-foreground resume (`MainActivity.onResume`)
On every `onResume`, MainActivity calls `maybeResumeOnForeground()` which:
1. Reads the `resume_on_app_foreground` DataStore flag (default `true`, exposed via `SettingsViewModel.RESUME_ON_APP_FOREGROUND_KEY`).
2. Queries `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` for any device of type `TYPE_BLUETOOTH_A2DP`, `TYPE_BLUETOOTH_SCO`, or `TYPE_BLE_HEADSET` (gated to API 31+ since `BLE_HEADSET` was added in S).
3. Confirms `playbackController.playbackState.value` has a `currentSong` and `isPlaying == false`.

When all three hold, it calls `playbackController.play()`. This complements the BluetoothReceiver-driven auto-resume (which fires on the connect *event*) by handling the case where BT was already connected before the app came back to foreground. The query uses no runtime permission — `getDevices()` is a passive audio-route inspection and does not require `BLUETOOTH_CONNECT`.

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
