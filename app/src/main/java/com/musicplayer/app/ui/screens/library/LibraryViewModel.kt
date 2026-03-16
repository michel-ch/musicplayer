package com.musicplayer.app.ui.screens.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.domain.model.Album
import com.musicplayer.app.domain.model.Artist
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.model.SortOption
import com.musicplayer.app.domain.repository.MusicRepository
import com.musicplayer.app.domain.usecase.SortSongsUseCase
import com.musicplayer.app.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val sortSongsUseCase: SortSongsUseCase,
    val playbackController: PlaybackController,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _sortOption = MutableStateFlow(SortOption.TITLE_ASC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(
        musicRepository.getAllSongs(),
        _sortOption
    ) { allSongs, sort ->
        sortSongsUseCase(allSongs, sort)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums: StateFlow<List<Album>> = musicRepository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists: StateFlow<List<Artist>> = musicRepository.getArtists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val saved = prefs[SORT_KEY]
            if (saved != null) {
                runCatching { SortOption.valueOf(saved) }.getOrNull()?.let {
                    _sortOption.value = it
                }
            }
        }
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            musicRepository.refreshLibrary()
        }
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        viewModelScope.launch {
            dataStore.edit { it[SORT_KEY] = option.name }
        }
    }

    companion object {
        private val SORT_KEY = stringPreferencesKey("library_sort")
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
}
