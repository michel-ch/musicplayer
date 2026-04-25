# Architecture

Clean Architecture with three layers. All code lives under `com.musicplayer.app`.

```
┌─────────────────────────────────────────┐
│  UI (Compose screens + ViewModels)      │
├─────────────────────────────────────────┤
│  Domain (models, repository interfaces) │
├─────────────────────────────────────────┤
│  Data (Room, MediaStore, DataStore)      │
└─────────────────────────────────────────┘
```

## Domain Layer (`domain/`)

Pure Kotlin, no Android dependencies.

**Models** -- `Song`, `Album`, `Artist`, `Folder`, `Playlist`, `Genre`, `Year`, `Composer`, `SortOption` (enum, 26 values), `DeleteResult` (sealed class).

**Repository interfaces** -- `MusicRepository` (songs, albums, artists, folders, genres, years, composers, favorites, scanning, deletion) and `PlaylistRepository` (CRUD for playlists).

**Use cases** -- `SortSongsUseCase` applies a `SortOption` to a song list. Filename sort uses natural alphanumeric order so `7 - …` sorts before `19 - …`.

## Data Layer (`data/`)

**Room database** (`MusicDatabase`, version 2) with 4 entities:

| Entity | Table | Purpose |
|--------|-------|---------|
| `PlaylistEntity` | playlists | User-created playlists |
| `PlaylistSongEntity` | playlist_songs | Join table (playlist <-> song) |
| `FavoriteEntity` | favorites | Favorited song IDs |
| `SearchHistoryEntity` | search_history | Recent search queries |

Three DAOs: `PlaylistDao`, `FavoriteDao`, `SearchHistoryDao`.

**MediaScanner** -- discovers audio files via MediaStore and custom folder scanning (SAF). Builds genre/composer maps from jAudioTagger metadata.

**Repository implementations** -- `MusicRepositoryImpl` and `PlaylistRepositoryImpl` implement the domain interfaces.

## Playback System (`player/`)

All singletons, Hilt-injected:

- **`PlaybackController`** -- controls ExoPlayer through a Media3 `MediaController`. Exposes `PlaybackState` as `StateFlow` (current song, position, duration, playing/paused, shuffle, repeat mode). Persists queue to DataStore and to a fast-read SharedPreferences snapshot so the MiniPlayer is populated synchronously on process restart. Handles folder-continuation when a queue ends; evicts deleted songs from the queue via `onSongDeleted()`.

- **`QueueManager`** -- manages the playback queue and current index. Maintains original order separately for shuffle mode.

- **`SongDeletionHandler`** -- centralises the delete flow (request, Android-R+ confirmation, queue eviction, snackbar feedback) behind a single `delete(song)` call used by every list ViewModel.

- **`PlaybackService`** -- `MediaSessionService` running ExoPlayer in a foreground service. Handles audio focus, notification artwork, and initializes `EqualizerManager`.

- **`EqualizerManager`** (`player/audio/`) -- wraps Android AudioFX Equalizer and BassBoost. Persists band levels and presets to DataStore.

- **`RepeatMode`** -- enum with `OFF`, `ALL`, `ONE`. Cycles via `next()`.

## Dependency Injection (`di/`)

Three Hilt modules, all `@InstallIn(SingletonComponent::class)`:

| Module | Provides |
|--------|----------|
| `AppModule` | `DataStore<Preferences>` |
| `DatabaseModule` | `MusicDatabase`, `PlaylistDao`, `FavoriteDao`, `SearchHistoryDao` |
| `RepositoryModule` | Binds `MusicRepository`, `PlaylistRepository` |

All ViewModels use `@HiltViewModel` with constructor injection.

## UI Layer (`ui/`)

### Navigation

`Screen` sealed class defines 23 routes (17 static, 6 parameterized). `NavGraph.kt` wires them up.

Bottom navigation has 4 tabs: Library, Equalizer, Search, Queue. NowPlaying and Settings are modal screens that hide the bottom nav bar.

### Screens

Each feature has a screen composable and a ViewModel:

| Feature | Screen | ViewModel |
|---------|--------|-----------|
| Library | `LibraryScreen` | `LibraryViewModel` |
| All Songs | `AllSongsScreen` | `AllSongsViewModel` |
| Folders | `FolderBrowserScreen`, `FolderHierarchyScreen` | `FolderViewModel` |
| Albums | `AlbumsListScreen`, `AlbumDetailScreen` | `AlbumDetailViewModel` |
| Artists | `ArtistsListScreen`, `ArtistDetailScreen` | `ArtistDetailViewModel` |
| Album Artists | `AlbumArtistListScreen`, `AlbumArtistDetailScreen` | `AlbumArtistViewModel` |
| Composers | `ComposerListScreen`, `ComposerDetailScreen` | `ComposerViewModel` |
| Genres | `GenreListScreen`, `GenreDetailScreen` | `GenreViewModel` |
| Years | `YearListScreen`, `YearDetailScreen` | `YearViewModel` |
| Playlists | `PlaylistScreen` | `PlaylistViewModel` |
| Now Playing | `NowPlayingScreen` | `NowPlayingViewModel` |
| Equalizer | `EqualizerScreen` | `EqualizerViewModel` |
| Search | `SearchScreen` | `SearchViewModel` |
| Queue | `QueueScreen` | `QueueViewModel` |
| Settings | `SettingsScreen` | `SettingsViewModel` |

### Reusable Components

- `SongItem` -- song row with album art, playing indicator, duration badge
- `MiniPlayer` -- collapsed player bar shown at bottom of most screens
- `SongOptionsSheet` -- bottom sheet for song long-press actions (play next, add to playlist, share, delete)
- `CollectionOptionsSheet` -- bottom sheet for folder/album/artist actions (play all, shuffle, add to playlist)
- `AlbumArtImage` -- album art with fallback icon, uses Coil
- `SongArtModel` + `SongArtworkFetcher` -- custom Coil fetcher for per-song embedded artwork
- `SortMenu` -- dropdown for sort options
- `AlphabetFastScroller` -- side alphabet index for long lists
- `EqBandSlider` -- vertical slider for equalizer bands
- `RotaryKnob` -- circular knob control for EQ preamp/tone/limiter

## Data Flow

```
DataStore / Room / MediaStore
        |  Flow
  Repository (impl)
        |  Flow
    ViewModel (StateFlow via combine/map/flatMapLatest)
        |  collectAsState
   Compose UI
```

ViewModels expose `StateFlow` to the UI. Sort preferences are persisted to DataStore per-screen. Reactive chains use `combine`, `flatMapLatest`, and `map` on Flows.
