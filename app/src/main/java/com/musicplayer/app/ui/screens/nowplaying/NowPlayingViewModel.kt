package com.musicplayer.app.ui.screens.nowplaying

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.musicplayer.app.player.PlaybackController
import com.musicplayer.app.player.PlaybackState
import com.musicplayer.app.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    val playbackController: PlaybackController,
    val queueManager: QueueManager,
    dataStore: DataStore<Preferences>
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    val showWaveform: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[SettingsViewModel.SHOW_WAVEFORM_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val skipForwardSeconds: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[SettingsViewModel.SKIP_FORWARD_SECONDS_KEY] ?: SettingsViewModel.DEFAULT_SKIP_FORWARD_SECONDS }
        .stateIn(viewModelScope, SharingStarted.Lazily, SettingsViewModel.DEFAULT_SKIP_FORWARD_SECONDS)

    fun skipForward() = playbackController.seekBy(skipForwardSeconds.value * 1000L)

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun play() = playbackController.play()

    fun skipToNext() = playbackController.skipToNext()

    fun skipToPrevious() = playbackController.skipToPrevious()

    fun skipToPreviousForced() = playbackController.skipToPreviousForced()

    fun seekTo(fraction: Float) = playbackController.seekToFraction(fraction)

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun toggleRepeatMode() = playbackController.toggleRepeatMode()
}
