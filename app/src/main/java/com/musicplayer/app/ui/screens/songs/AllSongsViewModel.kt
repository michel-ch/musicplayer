package com.musicplayer.app.ui.screens.songs

import androidx.activity.result.IntentSenderRequest
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.domain.model.DeleteResult
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.model.SortOption
import com.musicplayer.app.domain.repository.MusicRepository
import com.musicplayer.app.domain.usecase.SortSongsUseCase
import com.musicplayer.app.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllSongsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val sortSongsUseCase: SortSongsUseCase,
    val playbackController: PlaybackController,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _sortOption = MutableStateFlow(SortOption.TITLE_ASC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val saved = prefs[SORT_KEY]
            if (saved != null) {
                _sortOption.value = SortOption.valueOf(saved)
            }
        }
    }

    val songs: StateFlow<List<Song>> = combine(
        musicRepository.getAllSongs(),
        _sortOption
    ) { allSongs, sort ->
        sortSongsUseCase(allSongs, sort)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Pending song waiting for delete confirmation from system dialog
    private val _pendingDeleteSong = MutableStateFlow<Song?>(null)

    // Emits an IntentSenderRequest when Android requires user confirmation to delete
    private val _deleteConfirmationRequest = MutableSharedFlow<IntentSenderRequest>()
    val deleteConfirmationRequest: SharedFlow<IntentSenderRequest> =
        _deleteConfirmationRequest.asSharedFlow()

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        viewModelScope.launch {
            dataStore.edit { it[SORT_KEY] = option.name }
        }
    }

    companion object {
        private val SORT_KEY = stringPreferencesKey("all_songs_sort")
    }

    fun playSong(song: Song, songs: List<Song>) {
        val index = songs.indexOf(song)
        playbackController.playSongs(songs, index.coerceAtLeast(0))
    }

    fun playAll(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            playbackController.playSongs(songs)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            when (val result = musicRepository.deleteSong(song)) {
                is DeleteResult.Deleted -> {
                    // Cache already updated in repository
                }
                is DeleteResult.RequiresConfirmation -> {
                    // Store the song and emit request for the UI to launch system dialog
                    _pendingDeleteSong.value = result.song
                    _deleteConfirmationRequest.emit(
                        IntentSenderRequest.Builder(result.intentSender).build()
                    )
                }
                is DeleteResult.Failed -> {
                    // Deletion failed silently — file may be inaccessible
                }
            }
        }
    }

    /** Called by the screen after the system delete dialog returns. */
    fun onDeleteConfirmationResult(wasGranted: Boolean) {
        val song = _pendingDeleteSong.value ?: return
        _pendingDeleteSong.value = null
        if (wasGranted) {
            viewModelScope.launch {
                // File was deleted by the system — just evict from in-memory cache
                musicRepository.removeFromCache(song)
            }
        }
    }
}
