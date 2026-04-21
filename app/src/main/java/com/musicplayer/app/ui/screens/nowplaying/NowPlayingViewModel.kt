package com.musicplayer.app.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import com.musicplayer.app.player.PlaybackController
import com.musicplayer.app.player.PlaybackState
import com.musicplayer.app.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    val playbackController: PlaybackController,
    val queueManager: QueueManager
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun play() = playbackController.play()

    fun skipToNext() = playbackController.skipToNext()

    fun skipToPrevious() = playbackController.skipToPrevious()

    fun skipToPreviousForced() = playbackController.skipToPreviousForced()

    fun seekTo(fraction: Float) = playbackController.seekToFraction(fraction)

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun toggleRepeatMode() = playbackController.toggleRepeatMode()
}
