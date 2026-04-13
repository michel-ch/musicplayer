package com.musicplayer.app.data.repository

import com.musicplayer.app.data.local.db.FavoriteDao
import com.musicplayer.app.data.local.db.PlaylistDao
import com.musicplayer.app.data.local.db.PlaylistEntity
import com.musicplayer.app.domain.model.Playlist
import com.musicplayer.app.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                val count = playlistDao.getPlaylistSongCount(entity.id).first()
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    songCount = count,
                    createdAt = entity.createdAt
                )
            }
        }

    override fun getSongIdsForPlaylist(playlistId: Long): Flow<List<Long>> =
        playlistDao.getSongIdsForPlaylist(playlistId)

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun deletePlaylist(playlistId: Long) =
        playlistDao.deletePlaylistById(playlistId)

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(playlist.copy(name = newName))
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        playlistDao.addSongAtEnd(playlistId, songId)
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeFromPlaylist(playlistId, songId)

    override fun getFavoriteIds(): Flow<List<Long>> =
        favoriteDao.getAllFavoriteIds()

    override fun isFavorite(songId: Long): Flow<Boolean> =
        favoriteDao.isFavorite(songId)

    override suspend fun toggleFavorite(songId: Long) {
        favoriteDao.toggleFavorite(songId)
    }
}
