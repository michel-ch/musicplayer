package com.musicplayer.app.ui.screens.queue

import androidx.lifecycle.ViewModel
import com.musicplayer.app.player.PlaybackController
import com.musicplayer.app.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    val playbackController: PlaybackController,
    val queueManager: QueueManager
) : ViewModel() {

    val queue = queueManager.queue
    val currentIndex = queueManager.currentIndex

    fun playAtIndex(index: Int) {
        playbackController.playAtIndex(index)
    }

    fun removeFromQueue(index: Int) {
        queueManager.removeFromQueue(index)
    }

    fun clearQueue() {
        queueManager.clear()
    }
}
