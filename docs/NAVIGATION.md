# Navigation

All routes defined in `Screen` sealed class (`ui/navigation/Screen.kt`).
Wired in `NavGraph.kt` with slide enter/exit animations.

## Routes

### Main Tabs (bottom nav)

| Route | Screen Object | Bottom Nav |
|-------|--------------|------------|
| `library` | Screen.Library | Yes |
| `equalizer` | Screen.Equalizer | Yes |
| `search` | Screen.Search | Yes |
| `queue` | Screen.Queue | Yes |

### Modal Screens (hide bottom nav)

| Route | Screen Object |
|-------|--------------|
| `now_playing` | Screen.NowPlaying |
| `settings` | Screen.Settings |

### List Screens

| Route | Screen Object |
|-------|--------------|
| `all_songs` | Screen.AllSongs |
| `albums_list` | Screen.AlbumsList |
| `artists_list` | Screen.ArtistsList |
| `genre_list` | Screen.GenreList |
| `year_list` | Screen.YearList |
| `folders_hierarchy` | Screen.FoldersHierarchy |
| `album_artists_list` | Screen.AlbumArtistsList |
| `composer_list` | Screen.ComposerList |
| `folders` | Screen.Folders |
| `playlists` | Screen.Playlists |
| `streams` | Screen.Streams |

### Detail Screens (parameterized)

| Route | Screen Object | Factory Method |
|-------|--------------|----------------|
| `album/{albumId}` | Screen.AlbumDetail | `createRoute(albumId: Long)` |
| `artist/{artistName}` | Screen.ArtistDetail | `createRoute(artistName: String)` |
| `genre/{genreName}` | Screen.GenreDetail | `createRoute(genreName: String)` |
| `year/{year}` | Screen.YearDetail | `createRoute(year: Int)` |
| `album_artist/{artistName}` | Screen.AlbumArtistDetail | `createRoute(artistName: String)` |
| `composer/{composerName}` | Screen.ComposerDetail | `createRoute(composerName: String)` |

## Adding a New Screen

1. Add route to `Screen` sealed class in `ui/navigation/Screen.kt`
2. Add composable entry in `NavGraph.kt`
3. If parameterized, add `createRoute()` factory method and declare `arguments` with `navArgument`
4. If it's a detail screen, use `backStackEntry.arguments?.getString("paramName")`
5. If bottom nav should be hidden, add the route to the hide-bottom-nav check in `MusicPlayerRoot.kt`
