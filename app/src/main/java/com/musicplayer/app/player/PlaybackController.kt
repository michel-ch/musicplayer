package com.musicplayer.app.player

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.repository.MusicRepository
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
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueManager: QueueManager,
    private val dataStore: DataStore<Preferences>,
    private val musicRepository: MusicRepository
) {
    private var mediaController: MediaController? = null
    private var playerListener: Player.Listener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val snapshotPrefs: SharedPreferences =
        context.getSharedPreferences("playback_snapshot", Context.MODE_PRIVATE)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)

    private data class PendingRestore(val queue: List<Song>, val index: Int, val position: Long)
    private val pendingRestore = AtomicReference<PendingRestore?>(null)

    @Volatile
    private var pendingPlay: Boolean = false

    private val _sourceRoute = MutableStateFlow<String?>(null)
    val sourceRoute: StateFlow<String?> = _sourceRoute.asStateFlow()

    companion object {
        private const val TAG = "PlaybackController"
        private val LAST_QUEUE_JSON = stringPreferencesKey("last_queue_json")
        private val LAST_ORIGINAL_JSON = stringPreferencesKey("last_original_json")
        private val LAST_QUEUE_INDEX = intPreferencesKey("last_queue_index")
        private val LAST_SOURCE_ROUTE = stringPreferencesKey("last_source_route")
        private val LAST_SHUFFLE = booleanPreferencesKey("last_shuffle")
        private val LAST_REPEAT = intPreferencesKey("last_repeat")
        private val LAST_POSITION = longPreferencesKey("last_position")
        private val LAST_SAVED_AT = longPreferencesKey("last_saved_at")
        val CONTINUE_TO_NEXT_FOLDER = booleanPreferencesKey("continue_to_next_folder")
        // Read by PlaybackService (BT auto-resume) and MainActivity (foreground resume)
        // to avoid resuming a track the user deliberately paused.
        val USER_PAUSED_KEY = booleanPreferencesKey("user_paused")
        private const val SNAPSHOT_QUEUE_JSON = "queue_json"
        private const val SNAPSHOT_ORIGINAL_JSON = "original_json"
        private const val SNAPSHOT_QUEUE_INDEX = "queue_index"
        private const val SNAPSHOT_SOURCE_ROUTE = "source_route"
        private const val SNAPSHOT_SHUFFLE = "shuffle"
        private const val SNAPSHOT_REPEAT = "repeat"
        private const val SNAPSHOT_POSITION = "position"
        private const val SNAPSHOT_SAVED_AT = "saved_at"
        private const val SNAPSHOT_USER_STOPPED = "user_stopped"
    }

    // Timestamp of the snapshot loaded by restoreSnapshotSync(), used to decide whether
    // the durable DataStore copy is newer (and should win) on cold-start restore.
    @Volatile
    private var lastSnapshotSavedAt: Long = 0L

    init {
        // Populate state synchronously from SharedPreferences snapshot so the
        // MiniPlayer is visible immediately after a process restart (e.g. the
        // app was killed while backgrounded after audio focus / BT loss).
        restoreSnapshotSync()
        connectToService()
        startPositionUpdater()
        restoreLastSong()
    }

    private fun restoreSnapshotSync() {
        try {
            val queueJson = snapshotPrefs.getString(SNAPSHOT_QUEUE_JSON, null) ?: return
            val savedIndex = snapshotPrefs.getInt(SNAPSHOT_QUEUE_INDEX, 0)
            val queueSongs = deserializeQueue(queueJson)
            if (queueSongs.isEmpty()) return
            val originalSongs = snapshotPrefs.getString(SNAPSHOT_ORIGINAL_JSON, null)
                ?.let { deserializeQueue(it) }?.takeIf { it.isNotEmpty() } ?: queueSongs
            val shuffle = snapshotPrefs.getBoolean(SNAPSHOT_SHUFFLE, false)
            val repeat = repeatModeFromOrdinal(snapshotPrefs.getInt(SNAPSHOT_REPEAT, 0))
            val position = snapshotPrefs.getLong(SNAPSHOT_POSITION, 0L)
            lastSnapshotSavedAt = snapshotPrefs.getLong(SNAPSHOT_SAVED_AT, 0L)
            val startIndex = savedIndex.coerceIn(0, queueSongs.size - 1)
            val song = queueSongs[startIndex]
            _repeatMode.value = repeat
            // Merge into existing state rather than replacing it, so live fields
            // (isPlaying) survive a runtime reconnect and shuffle/repeat aren't reset.
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    duration = song.duration,
                    shuffleEnabled = shuffle,
                    repeatMode = repeat,
                    currentPosition = position
                )
            }
            queueManager.restoreState(queueSongs, originalSongs, startIndex, shuffle)
            _sourceRoute.value = snapshotPrefs.getString(SNAPSHOT_SOURCE_ROUTE, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore snapshot", e)
        }
    }

    private fun writeSnapshot(
        queue: List<Song>,
        original: List<Song>,
        currentIndex: Int,
        route: String?,
        shuffle: Boolean,
        repeat: RepeatMode,
        position: Long,
        savedAt: Long
    ) {
        snapshotPrefs.edit().apply {
            if (queue.isNotEmpty()) {
                putString(SNAPSHOT_QUEUE_JSON, serializeQueue(queue))
                putString(SNAPSHOT_ORIGINAL_JSON, serializeQueue(original.ifEmpty { queue }))
                putInt(SNAPSHOT_QUEUE_INDEX, currentIndex)
                putBoolean(SNAPSHOT_SHUFFLE, shuffle)
                putInt(SNAPSHOT_REPEAT, repeat.ordinal)
                putLong(SNAPSHOT_POSITION, position)
                putLong(SNAPSHOT_SAVED_AT, savedAt)
            } else {
                remove(SNAPSHOT_QUEUE_JSON)
                remove(SNAPSHOT_ORIGINAL_JSON)
                remove(SNAPSHOT_QUEUE_INDEX)
                remove(SNAPSHOT_SHUFFLE)
                remove(SNAPSHOT_REPEAT)
                remove(SNAPSHOT_POSITION)
                remove(SNAPSHOT_SAVED_AT)
            }
            if (route != null) putString(SNAPSHOT_SOURCE_ROUTE, route)
            else remove(SNAPSHOT_SOURCE_ROUTE)
        }.apply()
    }

    private fun clearPersistedState() {
        lastSnapshotSavedAt = 0L
        snapshotPrefs.edit().clear().apply()
        scope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs.remove(LAST_QUEUE_JSON)
                prefs.remove(LAST_ORIGINAL_JSON)
                prefs.remove(LAST_QUEUE_INDEX)
                prefs.remove(LAST_SOURCE_ROUTE)
                prefs.remove(LAST_SHUFFLE)
                prefs.remove(LAST_REPEAT)
                prefs.remove(LAST_POSITION)
                prefs.remove(LAST_SAVED_AT)
            }
        }
    }

    private fun repeatModeFromOrdinal(ordinal: Int): RepeatMode =
        RepeatMode.values().getOrElse(ordinal) { RepeatMode.OFF }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    }

    private fun setUserPaused(value: Boolean) {
        scope.launch(Dispatchers.IO) {
            dataStore.edit { it[USER_PAUSED_KEY] = value }
        }
    }

    private fun restoreLastSong() {
        scope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val queueJson = prefs[LAST_QUEUE_JSON] ?: return@launch

            val queueSongs = deserializeQueue(queueJson)
            if (queueSongs.isEmpty()) return@launch

            // The snapshot (SharedPreferences) and this durable DataStore copy are written
            // by separate, unsynchronized paths, so a process kill between the two writes
            // can leave one stale. If restoreSnapshotSync() already seeded state and its
            // snapshot is at least as recent, keep it — connectToService loads the
            // controller from that queue. Only let DataStore win when it is strictly newer.
            val dataStoreSavedAt = prefs[LAST_SAVED_AT] ?: 0L
            if (queueManager.queue.value.isNotEmpty() && lastSnapshotSavedAt >= dataStoreSavedAt) {
                return@launch
            }

            val savedIndex = prefs[LAST_QUEUE_INDEX] ?: 0
            _sourceRoute.value = prefs[LAST_SOURCE_ROUTE]

            val originalSongs = prefs[LAST_ORIGINAL_JSON]?.let { deserializeQueue(it) }
                ?.takeIf { it.isNotEmpty() } ?: queueSongs
            val shuffle = prefs[LAST_SHUFFLE] ?: false
            val repeat = repeatModeFromOrdinal(prefs[LAST_REPEAT] ?: 0)
            val position = prefs[LAST_POSITION] ?: 0L
            val startIndex = savedIndex.coerceIn(0, queueSongs.size - 1)
            val currentSong = queueSongs[startIndex]

            _repeatMode.value = repeat
            _playbackState.update {
                it.copy(
                    currentSong = currentSong,
                    duration = currentSong.duration,
                    shuffleEnabled = shuffle,
                    repeatMode = repeat,
                    currentPosition = position
                )
            }
            queueManager.restoreState(queueSongs, originalSongs, startIndex, shuffle)

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
                        // Fresh service — load the saved queue at the saved position
                        controller.setMediaItems(
                            queueSongs.map { it.toMediaItem() },
                            startIndex,
                            position
                        )
                        controller.prepare()
                        controller.repeatMode = repeat.toPlayerRepeatMode()
                        if (pendingPlay) {
                            controller.play()
                            pendingPlay = false
                        }
                    }
                }
            } else {
                pendingRestore.set(PendingRestore(queueSongs, startIndex, position))
            }
        }
    }

    private fun saveLastSong(song: Song) {
        // Capture queue state synchronously on the calling (Main) thread to avoid a race
        // where QueueManager.clear() empties the queue before the IO coroutine reads it.
        val queue = queueManager.queue.value
        val original = queueManager.originalOrder
        val currentIndex = queueManager.currentIndex.value
        val route = _sourceRoute.value
        val shuffle = queueManager.shuffleEnabled.value
        val repeat = _repeatMode.value
        val position = mediaController?.currentPosition?.coerceAtLeast(0)
            ?: _playbackState.value.currentPosition
        // Never erase a valid snapshot with an empty one. A BT-triggered service
        // teardown can clear the queue between an event firing and this call —
        // writing emptyList() would `remove()` the SharedPreferences keys and
        // leave restoreSnapshotSync() with nothing to restore on next launch.
        // Intentional clears (last-song deletion) call clearPersistedState directly.
        if (queue.isEmpty()) {
            Log.w(TAG, "saveLastSong with empty queue; refusing destructive write")
            return
        }
        // Same timestamp on both stores so cold-start restore can tell which is newer.
        val savedAt = System.currentTimeMillis()
        lastSnapshotSavedAt = savedAt
        writeSnapshot(queue, original, currentIndex, route, shuffle, repeat, position, savedAt)
        scope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[LAST_QUEUE_JSON] = serializeQueue(queue)
                prefs[LAST_ORIGINAL_JSON] = serializeQueue(original.ifEmpty { queue })
                prefs[LAST_QUEUE_INDEX] = currentIndex
                prefs[LAST_SHUFFLE] = shuffle
                prefs[LAST_REPEAT] = repeat.ordinal
                prefs[LAST_POSITION] = position
                prefs[LAST_SAVED_AT] = savedAt
                if (route != null) prefs[LAST_SOURCE_ROUTE] = route else prefs.remove(LAST_SOURCE_ROUTE)
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
            obj.put("discNumber", song.discNumber)
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
            obj.put("composer", song.composer)
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
                    discNumber = obj.optInt("discNumber", 1),
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
                    albumArtUri = obj.optString("albumArtUri", "").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
                    composer = obj.optString("composer", "")
                )
            }
        } catch (e: Exception) {
            Log.e("PlaybackController", "Failed to deserialize queue", e)
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
            val controller = try {
                controllerFuture.get()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to obtain MediaController; retrying", e)
                scope.launch(Dispatchers.Main) {
                    delay(500)
                    connectToService()
                }
                return@addListener
            }
            if (controller == null) {
                Log.w(TAG, "MediaController future returned null; retrying")
                scope.launch(Dispatchers.Main) {
                    delay(500)
                    connectToService()
                }
                return@addListener
            }
            mediaController = controller
            setupPlayerListener()
            controller.repeatMode = _repeatMode.value.toPlayerRepeatMode()

            // A user-initiated Close (notification) sets this flag in the shared snapshot
            // file. Honour it by NOT reloading the queue into the fresh service — that
            // would respawn the dismissed notification. But KEEP the in-app mini-player
            // (currentSong + queue) so the bar stays visible and the user can resume from
            // inside the app; mark it user-paused so nothing auto-resumes.
            if (snapshotPrefs.getBoolean(SNAPSHOT_USER_STOPPED, false)) {
                snapshotPrefs.edit().remove(SNAPSHOT_USER_STOPPED).apply()
                setUserPaused(true)
                pendingPlay = false
                _playbackState.update { it.copy(isPlaying = false, currentPosition = 0) }
                return@addListener
            }

            val restored = pendingRestore.getAndSet(null)
            if (restored != null) {
                val songs = restored.queue
                val restoreIndex = restored.index
                // restoreLastSong() finished before connectToService() — apply the queue now
                mediaController?.apply {
                    if (mediaItemCount == 0) {
                        setMediaItems(songs.map { it.toMediaItem() }, restoreIndex, restored.position)
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
            } else {
                // connectToService() fired first — restoreLastSong() will handle
                // loading media items once it reads from DataStore.
                // However, if this is a reconnect and queueManager already has songs,
                // reload them into the fresh service so playback can resume.
                var queue = queueManager.queue.value
                // Fallback: a service kill triggered by a Bluetooth disconnect (auto-pause →
                // task removed) can leave us reconnecting with an empty queue and a null
                // currentSong.  Re-read the synchronous snapshot so the MiniPlayer recovers.
                if (queue.isEmpty() && _playbackState.value.currentSong == null) {
                    restoreSnapshotSync()
                    queue = queueManager.queue.value
                }
                if (queue.isNotEmpty()) {
                    mediaController?.apply {
                        if (mediaItemCount == 0) {
                            val idx = queueManager.currentIndex.value.coerceAtLeast(0)
                            val startPos = _playbackState.value.currentPosition.coerceAtLeast(0)
                            setMediaItems(queue.map { it.toMediaItem() }, idx, startPos)
                            prepare()
                            if (pendingPlay) { play(); pendingPlay = false }
                            // Ensure UI state is preserved after reloading into a fresh service
                            val song = queue.getOrNull(idx)
                            if (song != null && _playbackState.value.currentSong?.id != song.id) {
                                _playbackState.update {
                                    it.copy(currentSong = song, isPlaying = isPlaying)
                                }
                            }
                        } else {
                            // Service still running with items — sync state so MiniPlayer
                            // and play/pause reflect reality after a reconnect.
                            val currentIdx = currentMediaItemIndex
                            val validIdx = if (currentIdx in queue.indices) currentIdx
                                           else queueManager.currentIndex.value.coerceAtLeast(0)
                            if (validIdx in queue.indices) queueManager.skipToIndex(validIdx)
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
            }

            if (pendingPlay) {
                mediaController?.let { if (it.mediaItemCount > 0) { it.play() } }
                pendingPlay = false
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val controller = mediaController ?: return
        val listener = playerListener ?: createPlayerListener().also { playerListener = it }
        // Reconnects build a new controller; remove-before-add guards against stacking
        // the same listener, which would multiply every event and snapshot write.
        controller.removeListener(listener)
        controller.addListener(listener)
    }

    private fun createPlayerListener(): Player.Listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    // Playback is running again (by any means) — it is no longer
                    // "user paused", so headset/foreground auto-resume may act later.
                    setUserPaused(false)
                } else {
                    // Persist a fresh snapshot whenever we pause so a process kill while
                    // paused (e.g. after a BT toggle / audio focus loss) still recovers
                    // the queue on relaunch.
                    _playbackState.value.currentSong?.let { saveLastSong(it) }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Ignore transitions caused by playlist changes (e.g. artwork updates via replaceMediaItem)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
                // Service teardown / BT disconnect / focus loss can fire a null transition —
                // keep the existing currentSong so the MiniPlayer stays visible.
                if (mediaItem == null) return

                val index = mediaController?.currentMediaItemIndex ?: return
                queueManager.skipToIndex(index)
                val song = queueManager.currentSong.value
                // Only update if we resolved a valid song; never clear currentSong to null
                if (song != null) {
                    // Reset position/progress: the new track starts at 0. Without this the
                    // previous song's elapsed time leaks onto the next song in the UI
                    // (persistently while paused, since the position updater is idle then).
                    _playbackState.update {
                        it.copy(
                            currentSong = song,
                            currentPosition = 0,
                            // Use the song's known duration immediately; the player's
                            // duration is often still UNSET (0) at transition time and
                            // would otherwise show the previous track's total until
                            // STATE_READY lands (sticky while paused). STATE_READY then
                            // refines this with the precise player value.
                            duration = song.duration
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
                if (playbackState == Player.STATE_IDLE) {
                    // ExoPlayer can land in IDLE after an audio-sink failure (e.g. BT
                    // routing change). Re-prepare so the controller is usable again
                    // instead of leaving the MiniPlayer with controls that do nothing.
                    val controller = mediaController ?: return
                    if (controller.mediaItemCount > 0) {
                        controller.prepare()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Player error; attempting to recover", error)
                val controller = mediaController ?: return
                if (controller.mediaItemCount > 0) {
                    controller.prepare()
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                // Safety net: after any batch of player events, ensure our state
                // matches the actual player state.  This catches edge cases where
                // individual callbacks are missed during Bluetooth disconnect /
                // audio-source changes / service reconnection.
                val controller = mediaController ?: return
                var queue = queueManager.queue.value

                // If the in-memory queue was lost (e.g. service kill after BT-induced
                // pause) but the snapshot still has it, recover from disk before giving up.
                if (queue.isEmpty()) {
                    if (_playbackState.value.currentSong != null) {
                        // currentSong is intact — at least keep isPlaying in sync.
                        _playbackState.update { it.copy(isPlaying = controller.isPlaying) }
                        return
                    }
                    restoreSnapshotSync()
                    queue = queueManager.queue.value
                    if (queue.isEmpty()) return
                }

                val idx = controller.currentMediaItemIndex.let {
                    if (it in queue.indices) it
                    else queueManager.currentIndex.value.coerceIn(0, queue.size - 1)
                }
                if (idx !in queue.indices) return

                val song = queue[idx]
                val state = _playbackState.value
                if (state.currentSong?.id != song.id || state.isPlaying != controller.isPlaying) {
                    if (idx != queueManager.currentIndex.value) {
                        queueManager.skipToIndex(idx)
                    }
                    _playbackState.update {
                        it.copy(
                            currentSong = song,
                            isPlaying = controller.isPlaying,
                            duration = controller.duration.coerceAtLeast(0)
                        )
                    }
                }
            }
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
                scope.launch {
                    try {
                        val prefs = dataStore.data.first()
                        val continueToNextFolder = prefs[CONTINUE_TO_NEXT_FOLDER] ?: false
                        if (continueToNextFolder) {
                            playNextFolder()
                        } else {
                            _playbackState.update { it.copy(isPlaying = false) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to handle end of playback", e)
                        _playbackState.update { it.copy(isPlaying = false) }
                    }
                }
            }
        }
    }

    private suspend fun playNextFolder() {
        val currentSong = _playbackState.value.currentSong ?: return
        val currentFolderPath = currentSong.folderPath

        val allFolders = musicRepository.getFolders().first()
        val sortedFolders = allFolders.sortedBy { it.name.lowercase() }

        val currentFolderIdx = sortedFolders.indexOfFirst { it.path == currentFolderPath }
        if (currentFolderIdx < 0) {
            _playbackState.update { it.copy(isPlaying = false) }
            return
        }

        val nextFolderIdx = currentFolderIdx + 1
        if (nextFolderIdx >= sortedFolders.size) {
            // Last folder — stop
            _playbackState.update { it.copy(isPlaying = false) }
            return
        }

        val nextFolder = sortedFolders[nextFolderIdx]
        val nextSongs = musicRepository.getSongsByFolder(nextFolder.path).first()
            .sortedBy { it.fileName.lowercase() }

        if (nextSongs.isEmpty()) {
            _playbackState.update { it.copy(isPlaying = false) }
            return
        }

        scope.launch(Dispatchers.Main) {
            playSongs(nextSongs, 0)
        }
    }

    // Optimistically zero the elapsed time so the UI updates instantly on a skip —
    // even while paused, when the position updater isn't running. progress is derived
    // from currentPosition/duration, so it follows automatically.
    private fun resetPositionUi() {
        _playbackState.update { it.copy(currentPosition = 0) }
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

    fun playSongs(songs: List<Song>, startIndex: Int = 0, sourceRoute: String? = null) {
        if (sourceRoute != null) _sourceRoute.value = sourceRoute
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
                currentPosition = 0,
                duration = song?.duration ?: 0,
                // A fresh playSongs always loads natural order (callers that want
                // shuffle pass pre-shuffled songs or toggle afterwards). Without this
                // the previous set's shuffle flag leaks on, desyncing the icon and the
                // persisted snapshot from the actual order.
                shuffleEnabled = false
            )
        }
        song?.let { saveLastSong(it) }
    }

    fun play() {
        mediaController?.play()
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller != null) {
            if (controller.isPlaying) {
                setUserPaused(true)
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
                        var song = _playbackState.value.currentSong
                        if (song == null) {
                            Log.w(TAG, "togglePlayPause with empty queue and null currentSong; restoring snapshot")
                            restoreSnapshotSync()
                            val recoveredQueue = queueManager.queue.value
                            if (recoveredQueue.isNotEmpty()) {
                                controller.setMediaItems(
                                    recoveredQueue.map { it.toMediaItem() },
                                    queueManager.currentIndex.value.coerceAtLeast(0),
                                    0
                                )
                                controller.prepare()
                                controller.play()
                                return
                            }
                            song = _playbackState.value.currentSong ?: return
                        }
                        controller.setMediaItems(listOf(song.toMediaItem()), 0, 0)
                    }
                    controller.prepare()
                } else if (controller.playbackState == Player.STATE_ENDED) {
                    // Queue finished — seek back to current song start so play restarts it
                    controller.seekTo(queueManager.currentIndex.value.coerceAtLeast(0), 0)
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
                resetPositionUi()
            } else if (_repeatMode.value == RepeatMode.ALL) {
                controller.seekTo(0, 0)
                controller.play()
                resetPositionUi()
            }
        }
    }

    fun skipToPrevious() {
        mediaController?.let { controller ->
            if (controller.currentPosition > 3000) {
                controller.seekTo(0)
                resetPositionUi()
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
                resetPositionUi()
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
            resetPositionUi()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPosition = positionMs) }
    }

    fun seekToFraction(fraction: Float) {
        val duration = mediaController?.duration ?: return
        // Guard against C.TIME_UNSET (a large negative) right after a transition —
        // otherwise duration * fraction is garbage and seeks to a negative position.
        if (duration <= 0) return
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

    /**
     * Clear the entire queue and stop playback. Routes through the controller so the
     * player, QueueManager, UI state, and persisted snapshot are all cleared together —
     * otherwise QueueManager.clear() alone leaves a phantom song playing with the
     * MiniPlayer still visible over an "empty" queue.
     */
    fun clearQueue() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        queueManager.clear()
        _playbackState.update {
            it.copy(currentSong = null, isPlaying = false, currentPosition = 0, duration = 0)
        }
        clearPersistedState()
    }

    fun onSongDeleted(songId: Long) {
        val queue = queueManager.queue.value
        val index = queue.indexOfFirst { it.id == songId }
        if (index < 0) return

        val wasCurrent = index == queueManager.currentIndex.value
        val wasLast = queue.size == 1

        queueManager.removeFromQueue(index)
        mediaController?.removeMediaItem(index)

        if (wasLast) {
            mediaController?.stop()
            mediaController?.clearMediaItems()
            _playbackState.update {
                it.copy(currentSong = null, isPlaying = false, currentPosition = 0, duration = 0)
            }
            clearPersistedState()
            return
        }

        if (wasCurrent) {
            val newIndex = queueManager.currentIndex.value.coerceIn(0, queueManager.queue.value.size - 1)
            val newSong = queueManager.queue.value.getOrNull(newIndex) ?: return
            mediaController?.seekTo(newIndex, 0)
            _playbackState.update { it.copy(currentSong = newSong, currentPosition = 0) }
            saveLastSong(newSong)
        } else {
            _playbackState.value.currentSong?.let { saveLastSong(it) }
        }
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
