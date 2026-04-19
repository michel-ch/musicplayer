package com.musicplayer.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.data.local.db.SearchHistoryDao
import com.musicplayer.app.data.local.db.SearchHistoryEntity
import com.musicplayer.app.domain.model.Album
import com.musicplayer.app.domain.model.Artist
import com.musicplayer.app.domain.model.Composer
import com.musicplayer.app.domain.model.Folder
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.repository.MusicRepository
import com.musicplayer.app.player.PlaybackController
import com.musicplayer.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter(val label: String) {
    ALL("All"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    ALBUM_ARTISTS("Album Artists"),
    COMPOSERS("Composers"),
    FOLDERS("Folders"),
    GENRES("Genres")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val searchHistoryDao: SearchHistoryDao,
    val playbackController: PlaybackController
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    val searchHistory = searchHistoryDao.getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val songs: StateFlow<List<Song>> = _searchQuery.flatMapLatest { query ->
        musicRepository.searchSongs(query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums: StateFlow<List<Album>> = _searchQuery.flatMapLatest { query ->
        musicRepository.searchAlbums(query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists: StateFlow<List<Artist>> = _searchQuery.flatMapLatest { query ->
        musicRepository.searchArtists(query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folders: StateFlow<List<Folder>> = _searchQuery.flatMapLatest { query ->
        musicRepository.searchFolders(query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albumArtists: StateFlow<List<Artist>> = _searchQuery.flatMapLatest { query ->
        musicRepository.searchAlbumArtists(query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val composers: StateFlow<List<Composer>> = _searchQuery.flatMapLatest { query ->
        musicRepository.getComposers().map { composers ->
            if (query.isBlank()) emptyList()
            else composers.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: SearchFilter) {
        _filter.value = filter
    }

    fun saveSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searchHistoryDao.insert(SearchHistoryEntity(query = query.trim()))
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            searchHistoryDao.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearAll()
        }
    }

    fun playSong(song: Song, songs: List<Song>) {
        val index = songs.indexOf(song)
        playbackController.playSongs(songs, index.coerceAtLeast(0), Screen.Search.route)
    }

    fun shuffleAndPlay(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            playbackController.playSongs(songs.shuffled(), sourceRoute = Screen.Search.route)
        }
    }

    fun playAll(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            playbackController.playSongs(songs, sourceRoute = Screen.Search.route)
        }
    }
}
