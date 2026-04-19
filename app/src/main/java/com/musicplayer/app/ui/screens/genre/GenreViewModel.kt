package com.musicplayer.app.ui.screens.genre

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.domain.model.Genre
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.model.SortOption
import com.musicplayer.app.domain.repository.MusicRepository
import com.musicplayer.app.domain.usecase.SortSongsUseCase
import com.musicplayer.app.player.PlaybackController
import com.musicplayer.app.player.SongDeletionHandler
import com.musicplayer.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GenreViewModel @Inject constructor(
    musicRepository: MusicRepository,
    private val sortSongsUseCase: SortSongsUseCase,
    val playbackController: PlaybackController,
    private val dataStore: DataStore<Preferences>,
    private val deletionHandler: SongDeletionHandler,
) : ViewModel() {

    val genres: StateFlow<List<Genre>> = musicRepository.getGenres()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

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

    val genreSongs: StateFlow<List<Song>> = combine(
        _selectedGenre.flatMapLatest { genre ->
            if (genre != null) musicRepository.getSongsByGenre(genre) else flowOf(emptyList())
        },
        _sortOption
    ) { songs, sort ->
        sortSongsUseCase(songs, sort)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadGenre(genreName: String) {
        _selectedGenre.value = genreName
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        viewModelScope.launch {
            dataStore.edit { it[SORT_KEY] = option.name }
        }
    }

    companion object {
        private val SORT_KEY = stringPreferencesKey("genre_sort")
    }

    fun playSong(song: Song, songs: List<Song>) {
        val index = songs.indexOf(song)
        val route = _selectedGenre.value?.let { Screen.GenreDetail.createRoute(it) }
        playbackController.playSongs(songs, index.coerceAtLeast(0), route)
    }

    fun playAll(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            val route = _selectedGenre.value?.let { Screen.GenreDetail.createRoute(it) }
            playbackController.playSongs(songs, sourceRoute = route)
        }
    }

    fun deleteSong(song: Song) = deletionHandler.delete(song)
}
