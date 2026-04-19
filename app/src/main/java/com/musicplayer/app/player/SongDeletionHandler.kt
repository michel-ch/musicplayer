package com.musicplayer.app.player

import androidx.activity.result.IntentSenderRequest
import com.musicplayer.app.domain.model.DeleteResult
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongDeletionHandler @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _confirmationRequest = MutableSharedFlow<IntentSenderRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val confirmationRequest: SharedFlow<IntentSenderRequest> = _confirmationRequest.asSharedFlow()

    private val _feedback = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val feedback: SharedFlow<String> = _feedback.asSharedFlow()

    @Volatile
    private var pending: Song? = null

    fun delete(song: Song) {
        scope.launch {
            when (val result = musicRepository.deleteSong(song)) {
                is DeleteResult.Deleted -> {
                    playbackController.onSongDeleted(song.id)
                    _feedback.emit("Deleted \"${song.title}\"")
                }
                is DeleteResult.RequiresConfirmation -> {
                    pending = result.song
                    _confirmationRequest.emit(
                        IntentSenderRequest.Builder(result.intentSender).build()
                    )
                }
                is DeleteResult.Failed -> {
                    _feedback.emit("Couldn't delete \"${song.title}\"")
                }
            }
        }
    }

    fun onConfirmationResult(granted: Boolean) {
        val song = pending ?: return
        pending = null
        if (!granted) {
            scope.launch { _feedback.emit("Delete cancelled") }
            return
        }
        scope.launch {
            when (musicRepository.finalizeDelete(song)) {
                DeleteResult.Deleted -> {
                    playbackController.onSongDeleted(song.id)
                    _feedback.emit("Deleted \"${song.title}\"")
                }
                else -> _feedback.emit("Couldn't delete \"${song.title}\"")
            }
        }
    }
}
