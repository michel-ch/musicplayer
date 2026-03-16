package com.musicplayer.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.player.service.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueManager: QueueManager,
    private val dataStore: DataStore<Preferences>
) {
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)

    @Volatile
    private var pendingRestoreQueue: List<Song>? = null

    @Volatile
    private var pendingRestoreIndex: Int = 0

    @Volatile
    private var pendingPlay: Boolean = false

    companion object {
        private val LAST_QUEUE_JSON = stringPreferencesKey("last_queue_json")
        private val LAST_QUEUE_INDEX = intPreferencesKey("last_queue_index")
    }

    init {
        connectToService()
        startPositionUpdater()
        restoreLastSong()
    }

    private fun restoreLastSong() {
        scope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val queueJson = prefs[LAST_QUEUE_JSON] ?: return@launch
            val savedIndex = prefs[LAST_QUEUE_INDEX] ?: 0

            val queueSongs = deserializeQueue(queueJson)
            if (queueSongs.isEmpty()) return@launch

            val startIndex = savedIndex.coerceIn(0, queueSongs.size - 1)
            val currentSong = queueSongs[startIndex]

            _playbackState.update {
                it.copy(currentSong = currentSong, duration = currentSong.duration)
            }
            queueManager.setQueue(queueSongs, startIndex)

            val controller = mediaController
            if (controller != null) {
                scope.launch(Dispatchers.Main) {
                    if (controller.mediaItemCount > 0) {
                        // Service was already running (e.g. music playing in background).
                        // Sync our in-memory state to wherever the service currently is
                        // instead of resetting it to the saved index.
                        val currentIdx = controller.currentMediaItemIndex
                        if (currentIdx >= 0 && currentIdx < queueSongs.size) {
                            queueManager.skipToIndex(currentIdx)
                            val song = queueManager.currentSong.value
                            if (song != null) {
                                _playbackState.update {
                                    it.copy(
                                        currentSong = song,
                                        isPlaying = controller.isPlaying,
                                        duration = controller.duration.coerceAtLeast(0)
                                    )
                                }
                            }
                        }
                    } else {
                        // Fresh service — load the saved queue
                        controller.setMediaItems(
                            queueSongs.map { it.toMediaItem() },
                            startIndex,
                            0
                        )
                        controller.prepare()
                        if (pendingPlay) {
                            controller.play()
                            pendingPlay = false
                        }
                    }
                }
            } else {
                pendingRestoreQueue = queueSongs
                pendingRestoreIndex = startIndex
            }
        }
    }

    private fun saveLastSong(song: Song) {
        // Capture queue state synchronously on the calling (Main) thread to avoid a race
        // where QueueManager.clear() empties the queue before the IO coroutine reads it.
        val queue = queueManager.queue.value
        val currentIndex = queueManager.currentIndex.value
        scope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[LAST_QUEUE_JSON] = serializeQueue(queue)
                prefs[LAST_QUEUE_INDEX] = currentIndex
            }
        }
    }

    private fun serializeQueue(songs: List<Song>): String {
        val array = JSONArray()
        for (song in songs) {
            val obj = JSONObject()
            obj.put("id", song.id)
            obj.put("title", song.title)
            obj.put("artist", song.artist)
            obj.put("album", song.album)
            obj.put("albumId", song.albumId)
            obj.put("duration", song.duration)
            obj.put("trackNumber", song.trackNumber)
            obj.put("year", song.year)
            obj.put("genre", song.genre)
            obj.put("folderPath", song.folderPath)
            obj.put("folderName", song.folderName)
            obj.put("filePath", song.filePath)
            obj.put("fileName", song.fileName)
            obj.put("size", song.size)
            obj.put("dateAdded", song.dateAdded)
            obj.put("dateModified", song.dateModified)
            obj.put("uri", song.uri.toString())
            obj.put("albumArtUri", song.albumArtUri?.toString() ?: "")
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeQueue(json: String): List<Song> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Song(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    artist = obj.optString("artist", ""),
                    album = obj.optString("album", ""),
                    albumId = obj.optLong("albumId", 0L),
                    duration = obj.optLong("duration", 0L),
                    trackNumber = obj.optInt("trackNumber", 0),
                    year = obj.optInt("year", 0),
                    genre = obj.optString("genre", ""),
                    folderPath = obj.optString("folderPath", ""),
                    folderName = obj.optString("folderName", ""),
                    filePath = obj.optString("filePath", ""),
                    fileName = obj.optString("fileName", ""),
                    size = obj.optLong("size", 0L),
                    dateAdded = obj.optLong("dateAdded", 0L),
                    dateModified = obj.optLong("dateModified", 0L),
                    uri = Uri.parse(obj.getString("uri")),
                    albumArtUri = obj.optString("albumArtUri", "").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // Service was killed (e.g. notification close button).
                    // Clear the stale reference and reconnect so the user can
                    // resume playback without restarting the app.
                    mediaController = null
                    scope.launch(Dispatchers.Main) { connectToService() }
                }
            })
            .buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            setupPlayerListener()

            val songs = pendingRestoreQueue
            if (songs != null) {
                // restoreLastSong() finished before connectToService() — apply the queue now
                mediaController?.apply {
                    if (mediaItemCount == 0) {
                        setMediaItems(songs.map { it.toMediaItem() }, pendingRestoreIndex, 0)
                        prepare()
                        if (pendingPlay) { play(); pendingPlay = false }
                    } else {
                        // Service was already running — sync to its current position
                        val currentIdx = currentMediaItemIndex
                        if (currentIdx >= 0 && currentIdx < songs.size) {
                            queueManager.skipToIndex(currentIdx)
                            val song = queueManager.currentSong.value
                            if (song != null) {
                                _playbackState.update {
                                    it.copy(
                                        currentSong = song,
                                        isPlaying = isPlaying,
                                        duration = duration.coerceAtLeast(0)
                                    )
                                }
                            }
                        }
                    }
                }
                pendingRestoreQueue = null
            } else {
                // connectToService() fired first — restoreLastSong() will handle
                // loading media items once it reads from DataStore.
                // However, if this is a reconnect and queueManager already has songs,
                // reload them into the fresh service so playback can resume.
                val queue = queueManager.queue.value
                if (queue.isNotEmpty()) {
                    mediaController?.apply {
                        if (mediaItemCount == 0) {
                            val idx = queueManager.currentIndex.value.coerceAtLeast(0)
                            setMediaItems(queue.map { it.toMediaItem() }, idx, 0)
                            prepare()
                            if (pendingPlay) { play(); pendingPlay = false }
                        }
                    }
                }
            }

            if (pendingPlay) {
                mediaController?.let { if (it.mediaItemCount > 0) { it.play() } }
                pendingPlay = false
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Ignore transitions caused by playlist changes (e.g. artwork updates via replaceMediaItem)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return

                val index = mediaController?.currentMediaItemIndex ?: return
                queueManager.skipToIndex(index)
                val song = queueManager.currentSong.value
                // Only update if we resolved a valid song; never clear currentSong to null
                if (song != null) {
                    _playbackState.update {
                        it.copy(
                            currentSong = song,
                            duration = mediaController?.duration?.coerceAtLeast(0) ?: 0
                        )
                    }
                    saveLastSong(song)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _playbackState.update {
                        it.copy(duration = mediaController?.duration?.coerceAtLeast(0) ?: 0)
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
            }
        })
    }

    private fun handlePlaybackEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                mediaController?.seekTo(0)
                mediaController?.play()
            }
            RepeatMode.ALL -> {
                if (!queueManager.skipToNext()) {
                    queueManager.skipToIndex(0)
                    mediaController?.seekTo(0, 0)
                    mediaController?.play()
                }
            }
            RepeatMode.OFF -> {
                _playbackState.update { it.copy(isPlaying = false) }
            }
        }
    }

    private fun startPositionUpdater() {
        scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _playbackState.update {
                            it.copy(currentPosition = controller.currentPosition.coerceAtLeast(0))
                        }
                    }
                }
                delay(250)
            }
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        queueManager.setQueue(songs, startIndex)
        val mediaItems = songs.map { it.toMediaItem() }
        mediaController?.apply {
            setMediaItems(mediaItems, startIndex, 0)
            prepare()
            play()
        }
        val song = songs.getOrNull(startIndex)
        _playbackState.update {
            it.copy(
                currentSong = song,
                isPlaying = true,
                currentPosition = 0
            )
        }
        song?.let { saveLastSong(it) }
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller != null) {
            if (controller.isPlaying) {
                controller.pause()
            } else {
                // If player has no media items but we have a restored queue, re-prepare it
                if (controller.mediaItemCount == 0) {
                    val queue = queueManager.queue.value
                    val index = queueManager.currentIndex.value
                    if (queue.isNotEmpty()) {
                        controller.setMediaItems(
                            queue.map { it.toMediaItem() },
                            index.coerceAtLeast(0),
                            0
                        )
                    } else {
                        val song = _playbackState.value.currentSong ?: return
                        controller.setMediaItems(listOf(song.toMediaItem()), 0, 0)
                    }
                    controller.prepare()
                }
                controller.play()
            }
        } else {
            // Controller not connected yet — queue the play for when it's ready
            pendingPlay = true
        }
    }

    fun skipToNext() {
        mediaController?.let { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else if (_repeatMode.value == RepeatMode.ALL) {
                controller.seekTo(0, 0)
                controller.play()
            }
        }
    }

    fun skipToPrevious() {
        mediaController?.let { controller ->
            if (controller.currentPosition > 3000) {
                controller.seekTo(0)
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            }
        }
    }

    fun skipToPreviousForced() {
        mediaController?.let { controller ->
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            } else {
                controller.seekTo(0)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPosition = positionMs) }
    }

    fun seekToFraction(fraction: Float) {
        val duration = mediaController?.duration ?: return
        seekTo((duration * fraction).toLong())
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
        _playbackState.update { it.copy(shuffleEnabled = queueManager.shuffleEnabled.value) }
        // Rebuild media items from new queue order
        val songs = queueManager.queue.value
        val currentIndex = queueManager.currentIndex.value
        val currentPosition = mediaController?.currentPosition ?: 0
        mediaController?.apply {
            setMediaItems(songs.map { it.toMediaItem() }, currentIndex, currentPosition)
            prepare()
            if (_playbackState.value.isPlaying) play()
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = _repeatMode.value.next()
        _playbackState.update { it.copy(repeatMode = _repeatMode.value) }
        mediaController?.repeatMode = when (_repeatMode.value) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    fun playAtIndex(index: Int) {
        mediaController?.let { controller ->
            if (index in 0 until controller.mediaItemCount) {
                controller.seekTo(index, 0)
                controller.play()
            }
        }
    }

    fun addToQueue(song: Song) {
        queueManager.addToQueue(song)
        mediaController?.addMediaItem(song.toMediaItem())
    }

    private fun Song.toMediaItem(): MediaItem {
        // Prefer content URI, fall back to file path if content URI might be stale
        val mediaUri = if (uri.scheme == "content") {
            uri
        } else if (filePath.isNotEmpty()) {
            Uri.fromFile(java.io.File(filePath))
        } else {
            uri
        }
        return MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaId(id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
    }
}
