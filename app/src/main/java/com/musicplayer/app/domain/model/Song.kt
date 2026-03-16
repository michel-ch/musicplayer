package com.musicplayer.app.domain.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val trackNumber: Int,
    val discNumber: Int = 1,
    val year: Int,
    val genre: String,
    val folderPath: String,
    val folderName: String,
    val filePath: String,
    val fileName: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val composer: String = ""
) {
    val durationFormatted: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val fileFormat: String
        get() = fileName.substringAfterLast('.', "").lowercase()

    val formatBadge: String
        get() = "$durationFormatted | $fileFormat"
}
