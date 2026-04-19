# ViewModels

All ViewModels use `@HiltViewModel` with `@Inject constructor`. Screens get them via `hiltViewModel()`.

## State Pattern

Every ViewModel exposes state as `StateFlow`. UI collects via `collectAsStateWithLifecycle()`.

```kotlin
// Typical pattern
@HiltViewModel
class FooViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
}
```

## ViewModel Reference

### RootViewModel (`ui/RootViewModel.kt`)
- **Injects**: PlaybackController, SongDeletionHandler
- **Exposes**: `playbackController` (for MiniPlayer), `deletionHandler` (drives the root Snackbar + delete-confirmation launcher)

### LibraryViewModel (`ui/screens/library/`)
- **Injects**: MusicRepository, PlaybackController, DataStore, SortSongsUseCase
- **State**: `sortOption`, `songs`, `albums`, `artists`
- **Methods**: `setSortOption()`, `playSong()`, `playAll()`, `refreshLibrary()`
- **Persists**: sort preference to DataStore

### AllSongsViewModel (`ui/screens/songs/`)
- **Injects**: MusicRepository, PlaybackController, PlaylistRepository, DataStore, SortSongsUseCase, SongDeletionHandler
- **State**: `sortOption`, `songs`
- **Methods**: `setSortOption()`, `playSong()`, `deleteSong()` (one-liner that delegates to `SongDeletionHandler`)
- **Persists**: sort preference to DataStore

### NowPlayingViewModel (`ui/screens/nowplaying/`)
- **Injects**: PlaybackController, QueueManager
- **State**: delegates to `playbackController.playbackState`
- **Methods**: `togglePlayPause()`, `skipToNext()`, `skipToPrevious()`, `seekTo()`, `toggleShuffle()`, `toggleRepeatMode()`

### QueueViewModel (`ui/screens/queue/`)
- **Injects**: QueueManager, PlaybackController
- **State**: `queue`, `currentIndex` (from QueueManager)
- **Methods**: `playAtIndex()`, `removeFromQueue()`, `clearQueue()`

### SearchViewModel (`ui/screens/search/`)
- **Injects**: MusicRepository, PlaybackController, SearchHistoryDao
- **State**: `searchQuery`, `filter` (SearchFilter enum), `songs`, `albums`, `artists`, `folders`, `albumArtists`, `composers`, `searchHistory`
- **Methods**: `setQuery()`, `setFilter()`, `playSong()`, `saveSearch()`, `deleteSearchHistory()`

### PlaylistViewModel (`ui/screens/playlist/`)
- **Injects**: PlaylistRepository, MusicRepository, PlaybackController, DataStore, SortSongsUseCase
- **State**: `playlists`, `selectedPlaylistId`, `playlistSongs`, `sortOption`
- **Methods**: `selectPlaylist()`, `createPlaylist()`, `deletePlaylist()`, `renamePlaylist()`, `addSongToPlaylist()`, `removeSongFromPlaylist()`, `setSortOption()`

### EqualizerViewModel (`ui/screens/equalizer/`)
- **Injects**: EqualizerManager
- **State**: EQ bands, presets, bass boost
- **Methods**: `setBand()`, `selectPreset()`, `setBassBoost()`

### SettingsViewModel (`ui/screens/settings/`)
- **Injects**: DataStore, MusicRepository
- **State**: `darkMode`, `scanFolders`, `gaplessPlayback`, `crossfadeEnabled`, `crossfadeDuration`, `showAlbumArt`, `highResArt`, `showWaveform`, `autoResumeOnHeadset`, `keepScreenOn`, `showLockScreenControls`, `continueToNextFolder`
- **Methods**: toggle methods for each setting, `addScanFolder()`, `removeScanFolder()`

### Detail ViewModels (album, artist, genre, year, composer, albumartist, folder)

All follow the same pattern:
- **Injects**: MusicRepository, PlaybackController, PlaylistRepository (some), DataStore (some), SortSongsUseCase (some), SongDeletionHandler
- **State**: entity identifier + `songs: StateFlow<List<Song>>` + optional `sortOption`
- **Methods**: `load{Entity}()`, `playSong()`, `deleteSong()` (delegates to `SongDeletionHandler`), optional `setSortOption()`
- Located in their respective `ui/screens/{entity}/` directories

| ViewModel | File | Loads By |
|-----------|------|----------|
| AlbumDetailViewModel | `ui/screens/album/` | albumId: Long |
| ArtistDetailViewModel | `ui/screens/artist/` | artistName: String |
| AlbumArtistViewModel | `ui/screens/albumartist/` | artistName: String |
| GenreViewModel | `ui/screens/genre/` | genreName: String |
| YearViewModel | `ui/screens/year/` | year: Int |
| ComposerViewModel | `ui/screens/composer/` | composerName: String |
| FolderViewModel | `ui/screens/folder/` | folderPath: String |
