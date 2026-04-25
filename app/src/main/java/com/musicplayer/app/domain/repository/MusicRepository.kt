package com.musicplayer.app.domain.repository

import com.musicplayer.app.domain.model.Album
import com.musicplayer.app.domain.model.Artist
import com.musicplayer.app.domain.model.Composer
import com.musicplayer.app.domain.model.DeleteResult
import com.musicplayer.app.domain.model.Folder
import com.musicplayer.app.domain.model.Genre
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.model.Year
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getAllSongs(): Flow<List<Song>>
    fun getSongsByFolder(folderPath: String): Flow<List<Song>>
    fun getSongsByAlbum(albumId: Long): Flow<List<Song>>
    fun getSongsByArtist(artistName: String): Flow<List<Song>>
    fun getAlbums(): Flow<List<Album>>
    fun getArtists(): Flow<List<Artist>>
    fun getFolders(): Flow<List<Folder>>
    fun getGenres(): Flow<List<Genre>>
    fun getSongsByGenre(genreName: String): Flow<List<Song>>
    fun getYears(): Flow<List<Year>>
    fun getSongsByYear(year: Int): Flow<List<Song>>
    fun getComposers(): Flow<List<Composer>>
    fun getSongsByComposer(composerName: String): Flow<List<Song>>
    fun getAlbumArtists(): Flow<List<Artist>>
    fun getSongsByAlbumArtist(albumArtist: String): Flow<List<Song>>
    fun getFolderHierarchy(): Flow<Map<String, List<Folder>>>
    fun searchSongs(query: String): Flow<List<Song>>
    fun searchAlbums(query: String): Flow<List<Album>>
    fun searchArtists(query: String): Flow<List<Artist>>
    fun searchFolders(query: String): Flow<List<Folder>>
    fun searchAlbumArtists(query: String): Flow<List<Artist>>
    fun getScanFolders(): Flow<Set<String>>
    suspend fun addScanFolder(path: String)
    suspend fun addScanFolderUri(path: String, uri: String)
    suspend fun removeScanFolder(path: String)
    suspend fun refreshLibrary(force: Boolean = false)
    suspend fun deleteSong(song: Song): DeleteResult
    suspend fun finalizeDelete(song: Song): DeleteResult
    suspend fun removeFromCache(song: Song)
}
