package com.musicplayer.app.data.local.db

/** Room projection: a playlist row joined with its song count. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int
)
