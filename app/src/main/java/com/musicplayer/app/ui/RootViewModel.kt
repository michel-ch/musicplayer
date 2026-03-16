package com.musicplayer.app.ui

import androidx.lifecycle.ViewModel
import com.musicplayer.app.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val playbackController: PlaybackController
) : ViewModel()
