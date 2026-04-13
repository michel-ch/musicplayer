package com.musicplayer.app.player

import com.musicplayer.app.domain.model.Song

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val progress: Float
        get() = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
}
