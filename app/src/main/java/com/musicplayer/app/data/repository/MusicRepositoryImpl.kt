package com.musicplayer.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.musicplayer.app.data.local.db.CachedSongDao
import com.musicplayer.app.data.local.db.CachedSongEntity
import com.musicplayer.app.data.local.scanner.MediaScanner
import com.musicplayer.app.domain.model.Album
import com.musicplayer.app.domain.model.Artist
import com.musicplayer.app.domain.model.Composer
import com.musicplayer.app.domain.model.DeleteResult
import com.musicplayer.app.domain.model.Folder
import com.musicplayer.app.domain.model.Genre
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.model.Year
import com.musicplayer.app.domain.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaScanner: MediaScanner,
    private val dataStore: DataStore<Preferences>,
    private val cachedSongDao: CachedSongDao
) : MusicRepository {

    companion object {
        private const val TAG = "MusicRepository"
        private val SCAN_FOLDERS_KEY = stringSetPreferencesKey("scan_folders")
        private val SCAN_FOLDER_URIS_KEY = stringSetPreferencesKey("scan_folder_uris")
    }

    private val songsCache = MutableStateFlow<List<Song>>(emptyList())

    @Volatile
    private var diskCacheLoaded = false

    @Volatile
    private var scannedThisSession = false

    override fun getAllSongs(): Flow<List<Song>> = songsCache

    override fun getSongsByFolder(folderPath: String): Flow<List<Song>> =
        songsCache.map { songs -> songs.filter { it.folderPath == folderPath } }

    override fun getSongsByAlbum(albumId: Long): Flow<List<Song>> =
        songsCache.map { songs -> songs.filter { it.albumId == albumId } }

    override fun getSongsByArtist(artistName: String): Flow<List<Song>> =
        songsCache.map { songs -> songs.filter { it.artist.equals(artistName, ignoreCase = true) } }

    override fun getAlbums(): Flow<List<Album>> =
        songsCache.map { songs ->
            songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                Album(
                    id = albumId,
                    name = first.album,
                    artist = first.artist,
                    songCount = albumSongs.size,
                    albumArtUri = first.albumArtUri
                )
            }.sortedBy { it.name.lowercase() }
        }

    override fun getArtists(): Flow<List<Artist>> =
        songsCache.map { songs ->
            songs.groupBy { it.artist.lowercase() }.map { (_, artistSongs) ->
                val albumCount = artistSongs.map { it.albumId }.distinct().size
                Artist(
                    id = artistSongs.first().id,
                    name = artistSongs.first().artist,
                    songCount = artistSongs.size,
                    albumCount = albumCount
                )
            }.sortedBy { it.name.lowercase() }
        }

    override fun getFolders(): Flow<List<Folder>> =
        songsCache.map { songs ->
            songs.groupBy { it.folderPath }.map { (path, folderSongs) ->
                Folder(
                    path = path,
                    name = folderSongs.first().folderName,
                    songCount = folderSongs.size,
                    albumArtUri = folderSongs.firstNotNullOfOrNull { it.albumArtUri }
                )
            }.sortedBy { it.name.lowercase() }
        }

    override fun getGenres(): Flow<List<Genre>> =
        songsCache.map { songs ->
            songs.filter { it.genre.isNotBlank() }
                .groupBy { it.genre.lowercase() }
                .map { (_, genreSongs) ->
                    Genre(
                        name = genreSongs.first().genre,
                        songCount = genreSongs.size
                    )
                }.sortedBy { it.name.lowercase() }
        }

    override fun getSongsByGenre(genreName: String): Flow<List<Song>> =
        songsCache.map { songs ->
            songs.filter { it.genre.equals(genreName, ignoreCase = true) }
        }

    override fun getYears(): Flow<List<Year>> =
        songsCache.map { songs ->
            songs.filter { it.year > 0 }
                .groupBy { it.year }
                .map { (year, yearSongs) ->
                    Year(year = year, songCount = yearSongs.size)
                }.sortedByDescending { it.year }
        }

    override fun getSongsByYear(year: Int): Flow<List<Song>> =
        songsCache.map { songs -> songs.filter { it.year == year } }

    override fun getComposers(): Flow<List<Composer>> =
        songsCache.map { songs ->
            songs.filter { it.composer.isNotBlank() }
                .groupBy { it.composer.lowercase() }
                .map { (_, composerSongs) ->
                    Composer(
                        name = composerSongs.first().composer,
                        songCount = composerSongs.size
                    )
                }.sortedBy { it.name.lowercase() }
        }

    override fun getSongsByComposer(composerName: String): Flow<List<Song>> =
        songsCache.map { songs ->
            songs.filter { it.composer.equals(composerName, ignoreCase = true) }
        }

    override fun getAlbumArtists(): Flow<List<Artist>> =
        songsCache.map { songs ->
            songs.groupBy { it.artist.lowercase() }.map { (_, artistSongs) ->
                val albumIds = artistSongs.map { it.albumId }.distinct()
                Artist(
                    id = artistSongs.first().id,
                    name = artistSongs.first().artist,
                    songCount = artistSongs.size,
                    albumCount = albumIds.size
                )
            }.filter { it.albumCount > 0 }.sortedBy { it.name.lowercase() }
        }

    override fun getSongsByAlbumArtist(albumArtist: String): Flow<List<Song>> =
        songsCache.map { songs ->
            songs.filter { it.artist.equals(albumArtist, ignoreCase = true) }
        }

    override fun getFolderHierarchy(): Flow<Map<String, List<Folder>>> =
        songsCache.map { songs ->
            val allFolders = songs.groupBy { it.folderPath }.map { (path, folderSongs) ->
                Folder(
                    path = path,
                    name = folderSongs.first().folderName,
                    songCount = folderSongs.size,
                    albumArtUri = folderSongs.firstNotNullOfOrNull { it.albumArtUri }
                )
            }
            allFolders.groupBy { folder ->
                val parent = java.io.File(folder.path).parent ?: "/"
                parent
            }
        }

    override fun searchSongs(query: String): Flow<List<Song>> =
        songsCache.map { songs ->
            if (query.isBlank()) emptyList()
            else songs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                        song.artist.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true)
            }
        }

    override fun searchAlbums(query: String): Flow<List<Album>> =
        getAlbums().map { albums ->
            if (query.isBlank()) emptyList()
            else albums.filter { it.name.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
        }

    override fun searchArtists(query: String): Flow<List<Artist>> =
        getArtists().map { artists ->
            if (query.isBlank()) emptyList()
            else artists.filter { it.name.contains(query, ignoreCase = true) }
        }

    override fun searchFolders(query: String): Flow<List<Folder>> =
        getFolders().map { folders ->
            if (query.isBlank()) emptyList()
            else folders.filter { it.name.contains(query, ignoreCase = true) }
        }

    override fun searchAlbumArtists(query: String): Flow<List<Artist>> =
        getAlbumArtists().map { artists ->
            if (query.isBlank()) emptyList()
            else artists.filter { it.name.contains(query, ignoreCase = true) }
        }

    override fun getScanFolders(): Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[SCAN_FOLDERS_KEY] ?: emptySet() }

    override suspend fun addScanFolder(path: String) {
        dataStore.edit { prefs ->
            val current = prefs[SCAN_FOLDERS_KEY] ?: emptySet()
            prefs[SCAN_FOLDERS_KEY] = current + path
        }
    }

    override suspend fun addScanFolderUri(path: String, uri: String) {
        dataStore.edit { prefs ->
            val current = prefs[SCAN_FOLDER_URIS_KEY] ?: emptySet()
            // Store as "path\n=uri" pairs
            prefs[SCAN_FOLDER_URIS_KEY] = current + "$path\n=$uri"
        }
    }

    override suspend fun removeScanFolder(path: String) {
        dataStore.edit { prefs ->
            val current = prefs[SCAN_FOLDERS_KEY] ?: emptySet()
            prefs[SCAN_FOLDERS_KEY] = current - path
            // Also remove matching URI entry
            val currentUris = prefs[SCAN_FOLDER_URIS_KEY] ?: emptySet()
            prefs[SCAN_FOLDER_URIS_KEY] = currentUris.filterNot { it.startsWith("$path\n=") }.toSet()
        }
    }

    private fun parseFolderUriMap(entries: Set<String>): Map<String, String> {
        return entries.mapNotNull { entry ->
            val sep = entry.indexOf("\n=")
            if (sep > 0) entry.substring(0, sep) to entry.substring(sep + 2) else null
        }.toMap()
    }

    override suspend fun refreshLibrary(force: Boolean) {
        if (!diskCacheLoaded) {
            val cached = cachedSongDao.getAll().map { it.toSong() }
            if (cached.isNotEmpty() && songsCache.value.isEmpty()) {
                songsCache.value = cached
            }
            diskCacheLoaded = true
        }
        if (scannedThisSession && !force) return
        scannedThisSession = true

        val prefs = dataStore.data.first()
        val folders = prefs[SCAN_FOLDERS_KEY] ?: emptySet()
        val uriEntries = prefs[SCAN_FOLDER_URIS_KEY] ?: emptySet()
        val uriMap = parseFolderUriMap(uriEntries)
        val scanned = mediaScanner.scanAllSongs(folders.toList(), uriMap)
        if (scanned != songsCache.value) {
            songsCache.value = scanned
        }
        cachedSongDao.replaceAll(scanned.map { it.toEntity() })
    }

    private fun Song.toEntity() = CachedSongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        folderPath = folderPath,
        folderName = folderName,
        filePath = filePath,
        fileName = fileName,
        size = size,
        dateAdded = dateAdded,
        dateModified = dateModified,
        uri = uri.toString(),
        albumArtUri = albumArtUri?.toString(),
        composer = composer
    )

    private fun CachedSongEntity.toSong() = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        folderPath = folderPath,
        folderName = folderName,
        filePath = filePath,
        fileName = fileName,
        size = size,
        dateAdded = dateAdded,
        dateModified = dateModified,
        uri = Uri.parse(uri),
        albumArtUri = albumArtUri?.let { Uri.parse(it) },
        composer = composer
    )

    override suspend fun deleteSong(song: Song): DeleteResult {
        val contentUri = resolveContentUri(song)

        if (contentUri != null) {
            try {
                val rows = context.contentResolver.delete(contentUri, null, null)
                if (rows > 0) {
                    songsCache.update { it.filter { s -> s.id != song.id } }
                    return DeleteResult.Deleted
                }
                Log.w(TAG, "delete($contentUri) returned 0 rows")
            } catch (e: SecurityException) {
                return requestDeleteConfirmation(song, contentUri, e)
                    ?: DeleteResult.Failed.also { Log.w(TAG, "SecurityException with no recovery path", e) }
            } catch (e: Exception) {
                Log.w(TAG, "contentResolver.delete threw", e)
            }
        }

        // Fallback: direct file delete (Android 9 and below, or pre-Q cases)
        if (song.filePath.isNotEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val file = java.io.File(song.filePath)
                if (file.exists() && file.delete()) {
                    songsCache.update { it.filter { s -> s.id != song.id } }
                    return DeleteResult.Deleted
                }
                Log.w(TAG, "File.delete failed for ${song.filePath}")
            } catch (e: Exception) {
                Log.w(TAG, "File.delete threw for ${song.filePath}", e)
            }
        }

        return DeleteResult.Failed
    }

    override suspend fun finalizeDelete(song: Song): DeleteResult {
        // R+: createDeleteRequest already performed the delete on user confirmation.
        // Q: the RecoverableSecurityException only granted permission — retry the delete now.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val contentUri = resolveContentUri(song)
            if (contentUri == null) {
                Log.w(TAG, "finalizeDelete: could not resolve content URI for ${song.filePath}")
                return DeleteResult.Failed
            }
            return try {
                val rows = context.contentResolver.delete(contentUri, null, null)
                if (rows > 0) {
                    songsCache.update { it.filter { s -> s.id != song.id } }
                    DeleteResult.Deleted
                } else {
                    Log.w(TAG, "finalizeDelete: delete returned 0 rows on Q")
                    DeleteResult.Failed
                }
            } catch (e: Exception) {
                Log.w(TAG, "finalizeDelete: delete retry threw on Q", e)
                DeleteResult.Failed
            }
        }

        songsCache.update { it.filter { s -> s.id != song.id } }
        return DeleteResult.Deleted
    }

    override suspend fun removeFromCache(song: Song) {
        songsCache.update { songs -> songs.filter { it.id != song.id } }
    }

    private fun requestDeleteConfirmation(
        song: Song,
        contentUri: Uri,
        e: SecurityException
    ): DeleteResult? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(contentUri)
            )
            return DeleteResult.RequiresConfirmation(pendingIntent.intentSender, song)
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val rse = e as? android.app.RecoverableSecurityException
            if (rse != null) {
                return DeleteResult.RequiresConfirmation(
                    rse.userAction.actionIntent.intentSender, song
                )
            }
        }
        return null
    }

    private fun resolveContentUri(song: Song): Uri? {
        if (song.uri.scheme == "content") return song.uri
        if (song.filePath.isBlank()) return null
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(song.filePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0)
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveContentUri query failed for ${song.filePath}", e)
            null
        }
    }

}
