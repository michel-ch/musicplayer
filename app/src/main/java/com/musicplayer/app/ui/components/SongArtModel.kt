package com.musicplayer.app.ui.components

import android.net.Uri

/**
 * Model for loading per-song embedded artwork via Coil.
 * The custom [SongArtworkFetcher] extracts embedded art from the audio file.
 * If no embedded art is found, the default placeholder icon is shown.
 */
data class SongArtModel(
    val songUri: Uri,
    val albumArtUri: Uri?,
    val filePath: String? = null
)
