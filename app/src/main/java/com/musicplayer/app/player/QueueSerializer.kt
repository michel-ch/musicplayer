package com.musicplayer.app.player

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musicplayer.app.domain.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Queue (de)serialization shared by [PlaybackController] (which persists the queue) and
 * PlaybackService (which restores it for system-initiated playback resumption when the
 * app process was killed and the controller does not exist yet).
 */
object QueueSerializer {
    private const val TAG = "QueueSerializer"

    const val SNAPSHOT_PREFS = "playback_snapshot"
    const val SNAPSHOT_QUEUE_JSON = "queue_json"
    const val SNAPSHOT_QUEUE_INDEX = "queue_index"
    const val SNAPSHOT_POSITION = "position"

    data class Snapshot(val queue: List<Song>, val index: Int, val position: Long)

    /** Read the synchronous SharedPreferences snapshot, or null when there is none. */
    fun readSnapshot(prefs: SharedPreferences): Snapshot? {
        val json = prefs.getString(SNAPSHOT_QUEUE_JSON, null) ?: return null
        val queue = deserialize(json)
        if (queue.isEmpty()) return null
        val index = prefs.getInt(SNAPSHOT_QUEUE_INDEX, 0).coerceIn(0, queue.size - 1)
        val position = prefs.getLong(SNAPSHOT_POSITION, 0L).coerceAtLeast(0L)
        return Snapshot(queue, index, position)
    }

    fun serialize(songs: List<Song>): String {
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

    fun deserialize(json: String): List<Song> {
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
            Log.e(TAG, "Failed to deserialize queue", e)
            emptyList()
        }
    }
}

fun Song.toMediaItem(): MediaItem {
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
