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
2. Results cached in `MutableStateFlow<List<Song>>` (in-memory)
3. Category queries (albums, artists, genres, etc.) filter from the cached song list
4. ViewModels collect these flows and expose them as `StateFlow` to UI
5. `refreshLibrary()` re-scans MediaStore and updates the cache

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

## DI Wiring

```
AppModule ──provides──→ DataStore<Preferences>
DatabaseModule ──provides──→ MusicDatabase, PlaylistDao, FavoriteDao, SearchHistoryDao
RepositoryModule ──binds──→ MusicRepositoryImpl : MusicRepository
                 ──binds──→ PlaylistRepositoryImpl : PlaylistRepository

PlaybackController (@Singleton, @Inject constructor)
QueueManager (@Singleton, @Inject constructor)
MediaScanner (@Singleton, @Inject constructor)
```

All ViewModels use `@HiltViewModel` — Hilt resolves constructor dependencies automatically.
