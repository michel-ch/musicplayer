package com.musicplayer.app.ui.screens.albumartist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val sortSongsUseCase: SortSongsUseCase,
    val playbackController: PlaybackController,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val albumArtists: StateFlow<List<Artist>> = musicRepository.getAlbumArtists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedArtist = MutableStateFlow<String?>(null)

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

    val artistSongs: StateFlow<List<Song>> = combine(
        _selectedArtist.flatMapLatest { artist ->
            if (artist != null) musicRepository.getSongsByAlbumArtist(artist) else flowOf(emptyList())
        },
        _sortOption
    ) { songs, sort ->
        sortSongsUseCase(songs, sort)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadArtist(artistName: String) {
        _selectedArtist.value = artistName
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        viewModelScope.launch {
            dataStore.edit { it[SORT_KEY] = option.name }
        }
    }

    companion object {
        private val SORT_KEY = stringPreferencesKey("album_artist_sort")
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
        viewModelScope.launch { musicRepository.deleteSong(song) }
    }
}
