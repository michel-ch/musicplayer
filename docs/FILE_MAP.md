# File Map

Complete listing of every source file with its purpose. Base path: `app/src/main/java/com/musicplayer/app/`

## Entry Points

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Activity entry point, permission handling, Hilt setup. Observes the `keep_screen_on` DataStore flag while STARTED and toggles `FLAG_KEEP_SCREEN_ON` on the window — flag is released when the activity is backgrounded so it never blocks system standby. `onResume` calls `maybeResumeOnForeground()` which checks the `resume_on_app_foreground` setting, asks `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` whether a BT audio output (A2DP / SCO / BLE headset on API 31+) is connected, and resumes paused playback via the injected `PlaybackController`. |
| `MusicPlayerApp.kt` | Application class (@HiltAndroidApp), Coil image loader config |

## Domain Layer (`domain/`)

### Models (`domain/model/`)

| File | Fields |
|------|--------|
| `Song.kt` | id, title, artist, album, duration, year, genre, path, composer, uri, albumArtUri, formatted helpers |
| `Album.kt` | id, name, artist, songCount, albumArtUri |
| `Artist.kt` | id, name, songCount, albumCount |
| `Playlist.kt` | id, name, songCount, createdAt |
| `Genre.kt` | name, songCount |
| `Year.kt` | year, songCount |
| `Composer.kt` | name, songCount |
| `Folder.kt` | path, name, songCount |
| `SortOption.kt` | Enum with 29 sort options (title, artist, album, duration, date, size, track, disc, filename, year, genre, composer, format, shuffle) |
| `DeleteResult.kt` | Sealed class: `Deleted`, `RequiresConfirmation(intentSender, song)`, `Failed(message)` |

### Repository Interfaces (`domain/repository/`)

| File | Key Methods |
|------|-------------|
| `MusicRepository.kt` | `getAllSongs()`, `getSongsByFolder/Album/Artist/Genre/Year/Composer/AlbumArtist()`, `getAlbums()`, `getArtists()`, `getFolders()`, `getGenres()`, `getYears()`, `getComposers()`, `getAlbumArtists()`, `getFolderHierarchy()`, `searchSongs/Albums/Artists/Folders/AlbumArtists()`, `getScanFolders()`, `addScanFolder()`, `addScanFolderUri()`, `removeScanFolder()`, `refreshLibrary(force = false)`, `deleteSong()`, `finalizeDelete()`, `removeFromCache()` |
| `PlaylistRepository.kt` | `getAllPlaylists()`, `getSongIdsForPlaylist()`, `createPlaylist()`, `deletePlaylist()`, `renamePlaylist()`, `addSongToPlaylist()`, `removeSongFromPlaylist()`, `getFavoriteIds()`, `isFavorite()`, `toggleFavorite()` |

### Use Cases (`domain/usecase/`)

| File | Purpose |
|------|---------|
| `SortSongsUseCase.kt` | Applies SortOption to a list of songs. Filename sort uses natural order (digit runs compared numerically, so "7 - …" precedes "19 - …"). |

## Data Layer (`data/`)

### Repository Implementations (`data/repository/`)

| File | Notes |
|------|-------|
| `MusicRepositoryImpl.kt` | Uses MediaScanner, in-memory cache via `MutableStateFlow<List<Song>>`, DataStore for scan folder prefs. On first `refreshLibrary()` of the process, hydrates the in-memory cache from the `cached_songs` Room table so the UI populates instantly, then runs MediaScanner once per session and writes the snapshot back via `cachedSongDao.replaceAll()`. Subsequent calls are no-ops unless `force = true`. No background workers — the cache is only refreshed while the app is in the foreground. |
| `PlaylistRepositoryImpl.kt` | Uses PlaylistDao + FavoriteDao from Room |

### Room Database (`data/local/db/`)

| File | Purpose |
|------|---------|
| `MusicDatabase.kt` | Room DB v3. v1→v2 adds search_history; v2→v3 adds cached_songs. |
| `PlaylistEntity.kt` | Table: `playlists` (id, name, createdAt) |
| `PlaylistSongEntity.kt` | Table: `playlist_songs` (playlistId+songId composite PK, position, addedAt) |
| `FavoriteEntity.kt` | Table: `favorites` (songId PK, addedAt) |
| `SearchHistoryEntity.kt` | Table: `search_history` (id, query, timestamp) |
| `CachedSongEntity.kt` | Table: `cached_songs`. Persistent snapshot of the scanned library (all `Song` fields, with `Uri`s stored as strings). |
| `PlaylistDao.kt` | CRUD for playlists + playlist-songs, song count queries |
| `FavoriteDao.kt` | add/remove/check favorites, get all favorite IDs |
| `SearchHistoryDao.kt` | insert/delete searches, getRecentSearches (limit 20) |
| `CachedSongDao.kt` | `getAll()`, `insertAll()`, `clear()`, `replaceAll()` (transactional clear+insert). Used only by `MusicRepositoryImpl` to back the library cache. |

### Media Scanner (`data/local/scanner/`)

| File | Purpose |
|------|---------|
| `MediaScanner.kt` | Discovers audio files via MediaStore + custom folder scanning |

## Playback System (`player/`)

| File | Purpose |
|------|---------|
| `PlaybackController.kt` | @Singleton. Controls ExoPlayer via Media3 MediaController. Exposes `playbackState: StateFlow<PlaybackState>`. Methods: `playSongs()`, `playAtIndex()`, `togglePlayPause()`, `skipToNext()`, `skipToPrevious()`, `seekToFraction()`, `toggleShuffle()`, `toggleRepeatMode()`, `onSongDeleted()`. Persists queue to DataStore **and** to a synchronous `playback_snapshot` SharedPreferences cache used to hydrate `_playbackState` in `init` before the UI subscribes — keeps the MiniPlayer visible across process restarts. Injects MusicRepository for folder-continuation. `onEvents` listener syncs state after every event batch for BT disconnect resilience and tolerates an empty in-memory queue (preserves existing `currentSong` or rehydrates from snapshot); `onMediaItemTransition(null, …)` is ignored so service teardown cannot clear `currentSong`. The reconnect path also falls back to `restoreSnapshotSync()` when `queueManager.queue` is empty *and* `currentSong` is null, so a service kill triggered by BT-induced auto-pause cannot drop the MiniPlayer. |
| `QueueManager.kt` | @Singleton. Manages queue + currentIndex. Maintains both shuffled and original order. Methods: `setQueue()`, `skipToIndex()`, `skipToNext()`, `skipToPrevious()`, `toggleShuffle()`, `addToQueue()`, `removeFromQueue()`, `clear()` |
| `SongDeletionHandler.kt` | @Singleton. Single entry point for song deletion: `delete(song)` issues the repository call, on Android R+ surfaces the `IntentSender` confirmation via `confirmationRequest: SharedFlow`, then on result calls `finalizeDelete()`, evicts the song from the queue, and emits user-facing `feedback: SharedFlow<String>` for the Snackbar. |
| `PlaybackState.kt` | Data class: currentSong, isPlaying, currentPosition, duration, shuffleEnabled, repeatMode, progress |
| `RepeatMode.kt` | Enum: OFF, ALL, ONE with `next()` cycle |
| `BluetoothReceiver.kt` | Handles Bluetooth device connect/disconnect events |
| `service/PlaybackService.kt` | MediaSessionService wrapping ExoPlayer. Foreground service, audio focus, media notification, initializes EqualizerManager. `onTaskRemoved` only calls `stopSelf()` when the queue is empty (`mediaItemCount == 0`) — keeps the session alive after BT-induced auto-pause so the user can resume on app re-open without losing state. |
| `audio/EqualizerManager.kt` | Wraps Android AudioFX Equalizer + BassBoost, persists settings to DataStore |

## DI Layer (`di/`)

| File | Provides |
|------|----------|
| `AppModule.kt` | `DataStore<Preferences>` singleton |
| `DatabaseModule.kt` | `MusicDatabase` singleton, `PlaylistDao`, `FavoriteDao`, `SearchHistoryDao` |
| `RepositoryModule.kt` | Binds `MusicRepositoryImpl → MusicRepository`, `PlaylistRepositoryImpl → PlaylistRepository` |

## UI Layer (`ui/`)

### Root

| File | Purpose |
|------|---------|
| `MusicPlayerRoot.kt` | Root composable. Sets up NavHost, bottom nav, MiniPlayer, the `StartIntentSenderForResult` launcher that drives Android R+ delete confirmation, and the `SnackbarHost` that shows feedback from `SongDeletionHandler`. |
| `RootViewModel.kt` | Exposes `playbackController` and `deletionHandler` to the root composable |

### Navigation (`ui/navigation/`)

| File | Purpose |
|------|---------|
| `Screen.kt` | Sealed class defining all routes (see NAVIGATION.md) |
| `NavGraph.kt` | Composable NavHost with slide animations, wires all screens |

### Theme (`ui/theme/`)

| File | Purpose |
|------|---------|
| `Theme.kt` | Material3 theme setup |
| `Color.kt` | Color palette (grey/white/black, PowerampPrimary = #E0E0E0) |
| `Type.kt` | Typography definitions |

### Reusable Components (`ui/components/`)

| File | Purpose |
|------|---------|
| `SongItem.kt` | Song list row with album art + playing indicator. Uses `combinedClickable` for onClick + onLongClick |
| `MiniPlayer.kt` | Draggable mini player bar with swipe-to-skip gestures |
| `SortMenu.kt` | Dropdown with 29 sort options |
| `AlbumArtImage.kt` | Album artwork display with fallback |
| `AlphabetFastScroller.kt` | A-Z fast scroll sidebar |
| `SongArtModel.kt` | Data class for album art sources (uri, albumArtUri, filePath) |
| `SongArtworkFetcher.kt` | Custom Coil fetcher for per-song embedded artwork |
| `EqBandSlider.kt` | Single EQ frequency band slider |
| `RotaryKnob.kt` | Rotary knob control |
| `SongOptionsSheet.kt` | ModalBottomSheet for song long-press actions |
| `CollectionOptionsSheet.kt` | ModalBottomSheet for folder/album/artist long-press actions |
| `ListOptionsDialog.kt` | Dialog for selecting from a list |

### Screens (`ui/screens/`)

| Screen | Files | ViewModel | Purpose |
|--------|-------|-----------|---------|
| Library | `library/LibraryScreen.kt`, `LibraryViewModel.kt` | LibraryViewModel | Main hub, category grid, recent songs |
| All Songs | `songs/AllSongsScreen.kt`, `AllSongsViewModel.kt` | AllSongsViewModel | Full song list with sort + delete |
| Now Playing | `nowplaying/NowPlayingScreen.kt`, `NowPlayingViewModel.kt` | NowPlayingViewModel | Full-screen player with album art + controls. Horizontal swipe on the artwork skips tracks; swipe is clamped at queue edges (respects repeat-all for next). Seeking via the progress slider auto-resumes playback when paused. |
| Queue | `queue/QueueScreen.kt`, `QueueViewModel.kt` | QueueViewModel | Reorderable queue list |
| Search | `search/SearchScreen.kt`, `SearchViewModel.kt` | SearchViewModel | Search with filter tabs (all/albums/artists/folders) |
| Equalizer | `equalizer/EqualizerScreen.kt`, `EqualizerViewModel.kt` | EqualizerViewModel | 10-band EQ with presets |
| Settings | `settings/SettingsScreen.kt`, `SettingsViewModel.kt` | SettingsViewModel | App settings (dark mode, playback, display) |
| Albums List | `albums/AlbumsListScreen.kt` | (shared) | Grid of albums |
| Album Detail | `album/AlbumDetailScreen.kt`, `AlbumDetailViewModel.kt` | AlbumDetailViewModel | Songs in album |
| Artists List | `artists/ArtistsListScreen.kt` | (shared) | List of artists |
| Artist Detail | `artist/ArtistDetailScreen.kt`, `ArtistDetailViewModel.kt` | ArtistDetailViewModel | Songs by artist |
| Album Artists | `albumartist/AlbumArtistListScreen.kt`, `AlbumArtistDetailScreen.kt`, `AlbumArtistViewModel.kt` | AlbumArtistViewModel | Album artists list + detail |
| Genres | `genre/GenreListScreen.kt`, `GenreDetailScreen.kt`, `GenreViewModel.kt` | GenreViewModel | Genres list + detail |
| Years | `year/YearListScreen.kt`, `YearDetailScreen.kt`, `YearViewModel.kt` | YearViewModel | Years list + detail |
| Composers | `composer/ComposerListScreen.kt`, `ComposerDetailScreen.kt`, `ComposerViewModel.kt` | ComposerViewModel | Composers list + detail |
| Folders | `folder/FolderHierarchyScreen.kt`, `FolderBrowserScreen.kt`, `FolderViewModel.kt` | FolderViewModel | Folder tree + folder songs |
| Streams | `streams/StreamsScreen.kt` | — | Streams/radio |
