package com.musicplayer.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_songs")
data class CachedSongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String,
    val folderPath: String,
    val folderName: String,
    val filePath: String,
    val fileName: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val uri: String,
    val albumArtUri: String?,
    val composer: String
)
