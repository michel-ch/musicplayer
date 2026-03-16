package com.musicplayer.app.domain.model

import android.net.Uri

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int,
    val albumArtUri: Uri? = null
)
