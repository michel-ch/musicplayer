# Database

Room database: `MusicDatabase` (`data/local/db/MusicDatabase.kt`)
- Name: `music_player.db`
- Version: 3
- Migration v1→v2: added `search_history` table
- Migration v2→v3: added `cached_songs` table (library cache)

## Schema

### `playlists`

| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PRIMARY KEY, auto-increment |
| name | String | |
| createdAt | Long | timestamp |

### `playlist_songs`

| Column | Type | Constraints |
|--------|------|-------------|
| playlistId | Long | PRIMARY KEY, FOREIGN KEY → playlists.id (CASCADE DELETE) |
| songId | Long | PRIMARY KEY |
| position | Int | |
| addedAt | Long | timestamp |

Indexed on: playlistId, songId

### `favorites`

| Column | Type | Constraints |
|--------|------|-------------|
| songId | Long | PRIMARY KEY |
| addedAt | Long | timestamp |

### `search_history`

| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PRIMARY KEY, auto-increment |
| query | String | |
| timestamp | Long | |

### `cached_songs`

Persistent snapshot of the scanned library so the UI can render instantly on startup. Replaced as a unit on each scan; never read by features other than `MusicRepositoryImpl`.

| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PRIMARY KEY |
| title | String | |
| artist | String | |
| album | String | |
| albumId | Long | |
| duration | Long | ms |
| trackNumber | Int | |
| discNumber | Int | |
| year | Int | |
| genre | String | |
| folderPath | String | |
| folderName | String | |
| filePath | String | |
| fileName | String | |
| size | Long | bytes |
| dateAdded | Long | epoch s |
| dateModified | Long | epoch s |
| uri | String | content:// or file:// |
| albumArtUri | String? | nullable |
| composer | String | |

## DAO Methods

### PlaylistDao (`PlaylistDao.kt`)

```
getAllPlaylists(): Flow<List<PlaylistEntity>>
getPlaylistById(id: Long): PlaylistEntity?
insertPlaylist(playlist: PlaylistEntity): Long
updatePlaylist(playlist: PlaylistEntity)
deletePlaylist(playlist: PlaylistEntity)
deletePlaylistById(playlistId: Long)
getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>
getSongIdsForPlaylist(playlistId: Long): Flow<List<Long>>
insertPlaylistSong(playlistSong: PlaylistSongEntity)
removeFromPlaylist(playlistId: Long, songId: Long)
clearPlaylist(playlistId: Long)
getPlaylistSongCount(playlistId: Long): Flow<Int>
```

### FavoriteDao (`FavoriteDao.kt`)

```
getAllFavoriteIds(): Flow<List<Long>>
isFavorite(songId: Long): Flow<Boolean>
addFavorite(favorite: FavoriteEntity)
removeFavorite(songId: Long)
```

### SearchHistoryDao (`SearchHistoryDao.kt`)

```
getRecentSearches(): Flow<List<SearchHistoryEntity>>  // limit 20
insert(entry: SearchHistoryEntity)
deleteById(id: Long)
clearAll()
```

### CachedSongDao (`CachedSongDao.kt`)

```
getAll(): List<CachedSongEntity>
insertAll(songs: List<CachedSongEntity>)
clear()
replaceAll(songs: List<CachedSongEntity>)  // @Transaction: clear + insertAll
```

## Adding a New Table

1. Create entity in `data/local/db/` with `@Entity` annotation
2. Create DAO interface with `@Dao`
3. Add entity to `@Database(entities = [...])` in `MusicDatabase.kt`
4. Add DAO abstract method to `MusicDatabase`
5. Bump database version and add migration in `DatabaseModule.kt`
6. Provide DAO in `DatabaseModule.kt` via `@Provides`
