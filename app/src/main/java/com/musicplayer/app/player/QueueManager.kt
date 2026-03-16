package com.musicplayer.app.player

import com.musicplayer.app.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor() {

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private var originalQueue: List<Song> = emptyList()

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        originalQueue = songs
        _queue.value = songs
        _currentIndex.value = startIndex
        updateCurrentSong()
    }

    fun skipToIndex(index: Int) {
        if (index in _queue.value.indices) {
            _currentIndex.value = index
            updateCurrentSong()
        }
    }

    fun skipToNext(): Boolean {
        val nextIndex = _currentIndex.value + 1
        return if (nextIndex < _queue.value.size) {
            _currentIndex.value = nextIndex
            updateCurrentSong()
            true
        } else false
    }

    fun skipToPrevious(): Boolean {
        val prevIndex = _currentIndex.value - 1
        return if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            updateCurrentSong()
            true
        } else false
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        if (_shuffleEnabled.value) {
            val current = _currentSong.value
            val shuffled = _queue.value.toMutableList().apply {
                shuffle()
                if (current != null) {
                    remove(current)
                    add(0, current)
                }
            }
            _queue.value = shuffled
            _currentIndex.value = 0
        } else {
            val current = _currentSong.value
            _queue.value = originalQueue
            _currentIndex.value = if (current != null) {
                originalQueue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            } else 0
        }
    }

    fun addToQueue(song: Song) {
        _queue.value = _queue.value + song
        originalQueue = originalQueue + song
    }

    fun removeFromQueue(index: Int) {
        if (index in _queue.value.indices) {
            val mutable = _queue.value.toMutableList()
            mutable.removeAt(index)
            _queue.value = mutable

            when {
                index < _currentIndex.value -> _currentIndex.value -= 1
                index == _currentIndex.value -> updateCurrentSong()
            }
        }
    }

    fun clear() {
        _queue.value = emptyList()
        originalQueue = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
    }

    private fun updateCurrentSong() {
        _currentSong.value = _queue.value.getOrNull(_currentIndex.value)
    }
}
