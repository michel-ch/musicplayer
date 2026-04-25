# Data Flow

## Data Sources

```
MediaStore (Android system)      Room Database           DataStore (Preferences)
        │                              │                         │
        ▼                              ▼                         ▼
   MediaScanner              PlaylistDao, FavoriteDao    Sort options, settings,
   (scans audio files)       SearchHistoryDao            scan folders, last queue,
        │                              │                 EQ settings
        ▼                              ▼                         │
  MusicRepositoryImpl         PlaylistRepositoryImpl              │
  (in-memory cache)           (playlist/favorite ops)             │
        │                              │                         │
        └──────────────┬───────────────┘                         │
                       ▼                                         │
              ViewModels (inject repos + DataStore)──────────────┘
                       │
                       ▼ (StateFlow)
                 Compose UI
```

## Music Library Flow

1. `MusicRepositoryImpl` uses `MediaScanner` to query MediaStore for audio files
2. Results cached in `MutableStateFlow<List<Song>>` (in-memory) and persisted to the Room `cached_songs` table
3. Category queries (albums, artists, genres, etc.) filter from the cached song list
4. ViewModels collect these flows and expose them as `StateFlow` to UI
5. `refreshLibrary(force)` first hydrates the in-memory cache from `cached_songs` (instant), then re-scans MediaStore once per app session and writes the new snapshot back to disk. Settings rescan / folder add-remove pass `force = true` to bypass the per-session guard. No background workers — work only happens while the app is in the foreground.

## Playlist/Favorites Flow

1. `PlaylistRepositoryImpl` uses Room DAOs directly
2. Playlist operations (create, delete, add song) go through DAO → Room → SQLite
3. All DAO queries return `Flow`, so UI updates reactively
4. Song resolution: DAO returns song IDs → ViewModel resolves full Song objects from MusicRepository cache

## Settings Flow

1. `SettingsViewModel` reads/writes directly to `DataStore<Preferences>`
2. Each setting has a DataStore key (e.g., `booleanPreferencesKey("dark_mode")`)
3. Settings flow is collected in ViewModel init block
4. Toggle methods write new values to DataStore, which triggers flow emission

## Playback Flow

1. Screen calls `playbackController.playSongs(songs, index)`
2. PlaybackController → QueueManager.setQueue() → updates queue StateFlow
3. PlaybackController → MediaController → sends MediaItems to ExoPlayer (in PlaybackService)
4. ExoPlayer plays → position updates → PlaybackController polls and updates `playbackState` StateFlow
5. UI collects `playbackState` and recomposes (MiniPlayer, NowPlayingScreen)
6. `onEvents` listener verifies state consistency after every event batch (resilience for BT disconnect / audio source changes)
7. On every track change, PlaybackController writes a synchronous snapshot (queue JSON, index, source route) to the `playback_snapshot` SharedPreferences file, so `_playbackState` can be rehydrated in `init` before the UI subscribes on a subsequent process start.

## Delete Flow

1. User long-presses a song → `SongOptionsSheet` → delete tapped
2. ViewModel calls `deletionHandler.delete(song)` (every list ViewModel exposes this)
3. `SongDeletionHandler` calls `musicRepository.deleteSong()`:
   - **Android ≤ Q**: direct `contentResolver.delete`. Resolves non-`content://` URIs via MediaStore before deleting.
   - **Android R+**: returns `DeleteResult.RequiresConfirmation(intentSender, song)`.
4. For RequiresConfirmation, the handler emits `confirmationRequest: SharedFlow<IntentSenderRequest>`; the `StartIntentSenderForResult` launcher in `MusicPlayerRoot` handles the system dialog.
5. Launcher result → `deletionHandler.onConfirmationResult(granted)` → `finalizeDelete()` updates cache (R+) or retries the delete (Q).
6. On success the handler calls `playbackController.onSongDeleted(songId)` (evicts from queue) and emits a `feedback: SharedFlow<String>` message picked up by the root `SnackbarHost`.

## Song List Auto-Scroll

When entering a song list screen that contains the currently playing (or paused) song, the list auto-scrolls so the current song appears at the top of the visible area. Implemented via `LaunchedEffect` + `rememberLazyListState` on: AllSongsScreen, FolderBrowserScreen, AlbumDetailScreen, ArtistDetailScreen, GenreDetailScreen, YearDetailScreen, ComposerDetailScreen, AlbumArtistDetailScreen, PlaylistScreen. QueueScreen uses its own scroll logic (centers ~4 items above current).

## DI Wiring

```
AppModule ──provides──→ DataStore<Preferences>
DatabaseModule ──provides──→ MusicDatabase, PlaylistDao, FavoriteDao, SearchHistoryDao, CachedSongDao
RepositoryModule ──binds──→ MusicRepositoryImpl : MusicRepository
                 ──binds──→ PlaylistRepositoryImpl : PlaylistRepository

PlaybackController (@Singleton, @Inject constructor)
QueueManager (@Singleton, @Inject constructor)
MediaScanner (@Singleton, @Inject constructor)
```

All ViewModels use `@HiltViewModel` — Hilt resolves constructor dependencies automatically.
