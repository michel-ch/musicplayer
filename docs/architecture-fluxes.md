# Architecture Fluxes (F1–F18)

Master list of the principal data/control flows in the app. Every diagram
([`architecture.mmd`](architecture.mmd), [`architecture.drawio`](architecture.drawio),
and the Mermaid block in the [README](../README.md)) is a *filtered view* of this
table — the `F#` is the join key across all of them.

Each flux is grounded in source (`file:line`, paths relative to
`app/src/main/java/com/musicplayer/app/`). When code and a diagram disagree, the
code wins — update the diagrams.

## Perimeters

| Id | Perimeter | Members |
|----|-----------|---------|
| `UI` | Presentation | Compose screens, ViewModels, `MiniPlayer`, `NowPlaying` |
| `CORE` | App core (singletons, `@Inject`) | `PlaybackController`, `QueueManager`, `EqualizerManager`, `SongDeletionHandler`, `MusicRepositoryImpl`, `PlaylistRepositoryImpl`, `MediaScanner` |
| `STORE` | Local storage | Room `music_player.db`, `DataStore<Preferences>`, `playback_snapshot` SharedPreferences |
| `SVC` | Foreground service | `PlaybackService` (`MediaSessionService` + `MediaSession`), ExoPlayer |
| `SYS` | Android system | MediaStore, AudioFX (Equalizer/BassBoost), AudioManager, Notification shade, SAF / file system |
| `DEV` | External device | Bluetooth / wired headset |

## Fluxes

| F# | Name | Category | Source → … → Target | Mechanism | Evidence |
|----|------|----------|---------------------|-----------|----------|
| F1 | Play a collection | runtime | UI → `PlaybackController.playSongs()` → `QueueManager.setQueue()` → `MediaController` → ExoPlayer (`PlaybackService`) | StateFlow + MediaController IPC | `player/PlaybackController.kt:744`, `player/QueueManager.kt` |
| F2 | Playback state to UI | runtime | ExoPlayer → `Player.Listener` + 250 ms position poller → `PlaybackController._playbackState` → UI (MiniPlayer/NowPlaying) | `StateFlow<PlaybackState>` | `player/PlaybackController.kt:729`, `:539`, `:553` |
| F3 | Transport controls | runtime | UI → `PlaybackController` (play/pause/skip/seek/shuffle/repeat) → `MediaController` → ExoPlayer | MediaController IPC | `player/PlaybackController.kt:770`–`909` |
| F4 | Library load (cache-first) | data | Room `cached_songs` → in-memory `songsCache` StateFlow (instant), then `MediaScanner` → StateFlow → `CachedSongDao.replaceAll()` write-back | suspend DAO + StateFlow + `@Transaction` | `data/repository/MusicRepositoryImpl.kt:256`–`274`, `data/local/db/CachedSongDao.kt:21` |
| F5 | Media scan | data, integration | MediaStore query + SAF `DocumentFile` walk + jAudioTagger metadata → `List<Song>` | ContentResolver + DocumentFile | `data/local/scanner/MediaScanner.kt:22`–`264` |
| F6 | Category projections | data | `songsCache` StateFlow → `map`/`combine` → albums/artists/folders/genres/years/composers → ViewModels → UI | reactive Flow operators | `data/repository/MusicRepositoryImpl.kt:67`–`184` |
| F7 | Playlists & favorites | data | ViewModel → `PlaylistRepositoryImpl` → Room DAO `Flow` → UI; counts via `getAllPlaylistsWithCount()` JOIN → `PlaylistWithCount` | Room DAO `Flow` | `data/repository/PlaylistRepositoryImpl.kt:19`–`60`, `data/local/db/PlaylistDao.kt:23` |
| F8 | Settings & sort prefs | data | ViewModels ↔ `DataStore<Preferences>` (per-key read/write) | DataStore Preferences | `ui/screens/settings/SettingsViewModel.kt:40`–`117` |
| F9 | Playback snapshot write | secrets-config | `PlaybackController` (on transition / pause) → `playback_snapshot` SharedPreferences (sync) **and** `DataStore` (durable): queue + original order + index + route + shuffle + repeat + position + `saved_at` | SharedPreferences + DataStore | `player/PlaybackController.kt:291`–`327`, `:549`, `:580` |
| F10 | Cold-start restore | secrets-config | `restoreSnapshotSync()` seeds state synchronously in `init`; `restoreLastSong()` overrides from DataStore only when its `saved_at` is newer (recency arbitration) | sync SharedPreferences + DataStore | `player/PlaybackController.kt:100`–`141`, `:208`–`289` |
| F11 | Service binding / reconnect | runtime | `PlaybackController` ⇄ `MediaController` ⇄ `MediaSession` (`PlaybackService`); reconnect + queue reload on `onDisconnected` | Media3 session IPC | `player/PlaybackController.kt:392`–`526`, `player/service/PlaybackService.kt:123`–`171` |
| F12 | Notification | integration | `PlaybackService` → `MediaSession` notification (album art via `MediaMetadataRetriever`) → system shade; Close → write `user_stopped` + `stopSelf()` | foreground service notification | `player/service/PlaybackService.kt:146`–`264` |
| F13 | Equalizer init + apply | ops | `AnalyticsListener.onAudioSessionIdChanged` → `EqualizerManager.initialize()` → AudioFX `Equalizer`/`BassBoost`; UI EQ edits → `applyEffects()` (base+preamp+tone per band); persisted to DataStore | AnalyticsListener + AudioFX + DataStore | `player/service/PlaybackService.kt:85`–`104`, `player/audio/EqualizerManager.kt:84`–`274` |
| F14 | Bluetooth/headset auto-resume | integration | Android ACL/headset broadcast (`RECEIVER_EXPORTED`) → `BluetoothReceiver` → DataStore gate (`AUTO_RESUME_HEADSET_KEY`, `USER_PAUSED_KEY`) → `MediaSession.player.play()` | BroadcastReceiver + DataStore | `player/service/PlaybackService.kt:186`–`219`, `player/BluetoothReceiver.kt:18`–`44` |
| F15 | Resume on app foreground | integration | `MainActivity.onResume()` → `maybeResumeOnForeground()` → `AudioManager` device check + DataStore gates (`RESUME_ON_APP_FOREGROUND_KEY`, `USER_PAUSED_KEY`) → `PlaybackController.play()` | lifecycle + AudioManager + DataStore | `MainActivity.kt:102`–`124` |
| F16 | Notification → app nav | integration | system notification `PendingIntent` (`ACTION_OPEN_NOW_PLAYING`) → `MainActivity.handleIntent()` → navigate to NowPlaying | Intent + StateFlow | `MainActivity.kt:137`–`143`, `player/service/PlaybackService.kt:108` |
| F17 | Safe delete | data, integration | UI → `SongDeletionHandler.delete()` → `MusicRepositoryImpl.deleteSong()` (MediaStore / SAF; Android R+ `IntentSender` confirm) → `onSongDeleted()` queue eviction + Snackbar | ContentResolver + IntentSender | `player/SongDeletionHandler.kt:40`–`76`, `data/repository/MusicRepositoryImpl.kt:323`–`389` |
| F18 | Runtime permissions | auth | `MainActivity` requests `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT` before scan/playback | `ActivityResultContracts` | `MainActivity.kt:171`–`194`, `AndroidManifest.xml:4` |

## Glossary

| Term | Meaning |
|------|---------|
| MediaController / MediaSession | Media3 client/host pair; the in-process controller drives the player living in the foreground service over IPC. |
| StateFlow | Hot Kotlin coroutine flow holding current state; the UI collects it with `collectAsStateWithLifecycle()`. |
| DataStore | Jetpack durable async key-value preference store. |
| `playback_snapshot` | A SharedPreferences file read **synchronously** in `init` so the MiniPlayer hydrates with no flicker on cold start. |
| `saved_at` | Epoch-millis timestamp written with every snapshot; used to pick the newer of the SharedPreferences vs DataStore copies on restore. |
| SAF | Storage Access Framework — user-granted persistent tree URIs for custom scan folders. |
| AudioFX | Android `android.media.audiofx` — `Equalizer` and `BassBoost` bound to the ExoPlayer audio session id. |
| `RECEIVER_EXPORTED` | Flag required on API 33+ to register a receiver for system (Bluetooth) broadcasts. |
