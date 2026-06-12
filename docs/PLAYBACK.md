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
- `skipToNext()` / `skipToPrevious()` / `skipToPreviousForced()` — each optimistically zeroes `currentPosition` so the elapsed time updates instantly (even while paused, when the position ticker is idle)
- `seekToFraction(fraction: Float)` — seek by 0.0–1.0 progress; guards against an `UNSET`/non-positive duration right after a transition
- `toggleShuffle()` / `toggleRepeatMode()`
- `clearQueue()` — stops the player and clears the queue, `_playbackState`, and the persisted snapshot together (used by QueueScreen's clear action so no orphaned song keeps playing)

### State Resilience
- **`onEvents` safety net**: After every batch of player events, verifies `currentSong` and `isPlaying` match the actual player state. If the in-memory queue was lost (e.g. service kill after BT-induced pause) but `currentSong` is still set, only `isPlaying` is reconciled; if `currentSong` is also null the listener falls back to `restoreSnapshotSync()` before reconciling. The chosen index also tolerates a stale `currentMediaItemIndex` by clamping to `queueManager.currentIndex`.
- **Service reconnection sync**: When the MediaController reconnects to a fresh service (`mediaItemCount == 0`), the queue is reloaded and `_playbackState` is explicitly synced to prevent the current song from disappearing from the UI. If the reconnect lands with an empty `queueManager.queue` *and* a null `currentSong` (process didn't fully restart but state was lost), the controller calls `restoreSnapshotSync()` to rehydrate from the SharedPreferences snapshot before loading items into the new service.
- **`onMediaItemTransition` null guard**: Service teardown, focus loss, and similar events can fire a transition with `mediaItem == null` under a reason other than `PLAYLIST_CHANGED`; the listener explicitly ignores these so `currentSong` cannot be clobbered.
- **`MediaController.buildAsync()` retry**: `controllerFuture.get()` is wrapped in a try/catch and the result is null-checked. On exception or null result the controller schedules `connectToService()` again after 500 ms so a transient failure (e.g. `ForegroundServiceStartNotAllowedException` on API 31+) does not strand the player with no listener attached.
- **`onPlayerError` recovery**: Non-fatal ExoPlayer failures (e.g. `AudioSink` errors triggered by a Bluetooth routing change) are caught by an explicit `onPlayerError` handler that calls `controller.prepare()` to reset the pipeline. `currentSong` is preserved so the MiniPlayer stays visible.
- **`STATE_IDLE` recovery**: `onPlaybackStateChanged` calls `controller.prepare()` whenever the player lands in `STATE_IDLE` with `mediaItemCount > 0`. This complements `onPlayerError` for the cases where ExoPlayer transitions to IDLE without firing a separate error callback.
- **Pause-time snapshot**: `onIsPlayingChanged(false)` calls `saveLastSong(currentSong)` so a process kill while paused — common after a Bluetooth toggle — leaves a fresh snapshot on disk and the MiniPlayer recovers on the next launch.
- **`togglePlayPause` snapshot fallback**: If the user taps play while the controller has no media items and `currentSong` is null, the empty branch calls `restoreSnapshotSync()` and replays the recovered queue instead of silently returning.
- **End-of-playback error containment**: `handlePlaybackEnded` wraps the folder-continuation coroutine in a try/catch and resets `isPlaying = false` on failure, so a repository error never leaves the player in a half-updated state.
- **Position reset on track change**: `onMediaItemTransition` resets `currentPosition` to 0 and seeds `duration` from the resolved `Song.duration` (the player's own duration is frequently `UNSET`/0 at transition time and would otherwise show the previous track's elapsed time — sticky while paused). `STATE_READY` later refines `duration` with the precise player value.
- **Single reused player listener**: the `Player.Listener` is built once and re-attached with remove-before-add on every reconnect, so reconnects can't stack listeners (which would multiply every event and snapshot write).
- **User-pause tracking**: a deliberate pause writes `USER_PAUSED_KEY` to DataStore; it's cleared whenever playback actually resumes. Both auto-resume paths (the BT receiver and the foreground-resume check) skip a track the user paused on purpose.

### Persistence
- Each save writes the **full playback state** — playing-order queue, the pre-shuffle `originalQueue`, current index, source route, shuffle flag, repeat mode, position, and a `saved_at` timestamp — to **both** the durable DataStore keys **and** the fast-read `playback_snapshot` SharedPreferences file.
- `serializeQueue`/`deserializeQueue` round-trips every `Song` field (including `discNumber` and `composer`), so a restored queue equals the live library objects.
- `restoreSnapshotSync()` runs first in `init`, synchronously reading the SharedPreferences snapshot and **merging** it into `_playbackState` (so live fields like `isPlaying` aren't clobbered) plus seeding `QueueManager` via `restoreState(queue, original, index, shuffle)` before `connectToService()` starts. This closes the process-restart gap where the MiniPlayer would briefly hide while the async DataStore restore was in flight.
- **Recency arbitration**: the snapshot and DataStore copies are written by separate, unsynchronized paths, so a kill *between* the two writes can leave one stale. On cold start `restoreLastSong()` only lets the DataStore copy override the snapshot-seeded state when its `saved_at` is **strictly newer**; otherwise it keeps the snapshot and lets `connectToService()` load the controller from it. This prevents resurrecting a stale queue or landing on the wrong song.
- Pausing writes a fresh snapshot (`onIsPlayingChanged(false)`); an intentional clear (last-song deletion, `clearQueue`, notification Close) goes through `clearPersistedState()`.
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
Maintains both original and shuffled queue. When shuffle toggled on, shuffles remaining songs. When toggled off, restores original order at current song position. `originalOrder` exposes the pre-shuffle list so the controller can persist it.

### Other state rules
- `setQueue()` resets `shuffleEnabled` to `false` — a fresh `playSongs` always loads natural order (callers wanting shuffle pass pre-shuffled songs or toggle afterward), so the flag can't leak from a previous track-set.
- `restoreState(queue, original, index, shuffle)` rehydrates the queue, original order, index, and shuffle flag from a persisted snapshot.
- `removeFromQueue(index)` also evicts the removed song from `originalQueue`, so toggling shuffle off can't resurrect a just-deleted song.

## PlaybackService (`player/service/PlaybackService.kt`)

Media3 `MediaSessionService` running ExoPlayer in foreground.

### Responsibilities
- Creates and manages ExoPlayer instance
- Sets up MediaSession for system media controls
- Manages audio focus
- Creates media notification (with a custom Close button)
- Initializes EqualizerManager once the audio session id is assigned
- Handles Bluetooth events via BluetoothReceiver

### EqualizerManager init
The audio session id is `0`/`UNSET` when `onCreate` builds the player (it's assigned when the audio renderer initializes, typically at first playback). The service therefore attaches an `AnalyticsListener` and calls `equalizerManager.initialize(audioSessionId)` from `onAudioSessionIdChanged` — re-attaching if the id changes after a route change. The eager `onCreate` init only runs on the rare chance the id is already valid.

### Lifecycle: `onTaskRemoved`
The service only calls `stopSelf()` when the queue is empty (`mediaItemCount == 0`). Tearing down on `!playWhenReady` would also kill the session whenever ExoPlayer auto-paused via `setHandleAudioBecomingNoisy` (e.g. a Bluetooth disconnect), and the user swiping the app away would then orphan the MiniPlayer's backing state. Keeping the service alive while paused-with-queue lets the user resume playback after a BT round-trip without losing context.

### Notification Close (`COMMAND_STOP_SERVICE`)
The custom Close button stops the player, clears media items, calls `stopForeground(STOP_FOREGROUND_REMOVE)`, and writes a `user_stopped` flag into the shared `playback_snapshot` file before `stopSelf()`. On reconnect the controller honours that flag by **keeping** the in-app mini-player (currentSong + queue) and marking it user-paused, but **not** reloading the queue into the fresh service — so the notification doesn't respawn and the bar stays so the user can resume from inside the app.

### Bluetooth auto-resume
`BluetoothReceiver` is registered with `RECEIVER_EXPORTED` (required on API 33+ for the OS-sent `ACTION_ACL_CONNECTED` / `ACTION_HEADSET_PLUG` broadcasts; `RECEIVER_NOT_EXPORTED` silently drops them). On a connect event it resumes only when `SettingsViewModel.AUTO_RESUME_HEADSET_KEY` is on (default off) **and** the player isn't user-paused. Reading a device's `bluetoothClass` needs `BLUETOOTH_CONNECT`, which `MainActivity` requests at runtime on API 31+; an unreadable class is treated as "assume audio" rather than dropping the event.

## App-foreground resume (`MainActivity.onResume`)
On every `onResume`, MainActivity calls `maybeResumeOnForeground()` which:
1. Reads the `resume_on_app_foreground` DataStore flag (default `true`, exposed via `SettingsViewModel.RESUME_ON_APP_FOREGROUND_KEY`). Also reads `PlaybackController.USER_PAUSED_KEY` and bails if the user paused on purpose.
2. Queries `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` for any device of type `TYPE_BLUETOOTH_A2DP`, `TYPE_BLUETOOTH_SCO`, or `TYPE_BLE_HEADSET` (gated to API 31+ since `BLE_HEADSET` was added in S).
3. Confirms `playbackController.playbackState.value` has a `currentSong` and `isPlaying == false`.

When all three hold, it calls `playbackController.play()`. This complements the BluetoothReceiver-driven auto-resume (which fires on the connect *event*) by handling the case where BT was already connected before the app came back to foreground. The query uses no runtime permission — `getDevices()` is a passive audio-route inspection and does not require `BLUETOOTH_CONNECT`.

## EqualizerManager (`player/audio/EqualizerManager.kt`)

Wraps Android AudioFX `Equalizer` and `BassBoost`.

- `initialize(audioSessionId)` (called from the service's `AnalyticsListener`) rebuilds the hardware effects, restores base bands + preamp/tone scalars, then re-applies them — so an audio-session-id change no longer silently drops the user's EQ.
- `applyEffects()` is the single place that writes the hardware: each band = base level (`_bandLevels`) + preamp offset + tone offset, clamped. `setBandLevel`/`usePreset`/`setPreampLevel`/`setToneBass`/`setToneTreble` all funnel through it. (Previously preamp and tone were applied independently and overwrote each other.)
- Persists band levels, preset, preamp, tone, and bass boost to DataStore.

## PlaybackState (`player/PlaybackState.kt`)

```kotlin
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    // Derived — follows currentPosition/duration automatically (UI reads it for the seek bar)
    val progress: Float
        get() = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
}
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
