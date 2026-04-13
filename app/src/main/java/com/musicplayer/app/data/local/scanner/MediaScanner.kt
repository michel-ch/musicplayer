package com.musicplayer.app.data.local.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.musicplayer.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun scanAllSongs(
        customFolderPaths: List<String> = emptyList(),
        customFolderUris: Map<String, String> = emptyMap()
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val genreMap = loadGenreMap()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.COMPOSER,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.Audio.Media.DISC_NUMBER)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection, projection.toTypedArray(), selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val composerCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER)
            val discCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val filePath = cursor.getString(dataCol) ?: continue
                val file = File(filePath)
                val folderPath = file.parent ?: ""
                val folderName = File(folderPath).name

                val albumId = cursor.getLong(albumIdCol)
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                val rawTrack = cursor.getInt(trackCol)
                val (discNumber, trackNumber) = if (discCol >= 0) {
                    // API 30+: use dedicated disc column
                    val disc = cursor.getInt(discCol).takeIf { it > 0 } ?: 1
                    val track = rawTrack.takeIf { it > 0 } ?: 0
                    disc to track
                } else {
                    // Older APIs: parse from combined track field (disc * 1000 + track)
                    if (rawTrack >= 1000) {
                        val disc = rawTrack / 1000
                        val track = rawTrack % 1000
                        disc to track
                    } else {
                        1 to rawTrack
                    }
                }

                val song = Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: file.nameWithoutExtension,
                    artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown Artist",
                    album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Unknown Album",
                    albumId = albumId,
                    duration = cursor.getLong(durationCol),
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    year = cursor.getInt(yearCol),
                    genre = genreMap[id] ?: "",
                    folderPath = folderPath,
                    folderName = folderName,
                    filePath = filePath,
                    fileName = cursor.getString(displayNameCol) ?: file.name,
                    size = cursor.getLong(sizeCol),
                    dateAdded = cursor.getLong(dateAddedCol),
                    dateModified = cursor.getLong(dateModifiedCol),
                    uri = ContentUris.withAppendedId(collection, id),
                    albumArtUri = albumArtUri,
                    composer = cursor.getString(composerCol) ?: ""
                )
                songs.add(song)
            }
        }

        if (customFolderPaths.isNotEmpty()) {
            scanCustomFolders(customFolderPaths, customFolderUris, songs)
        }

        songs
    }

    @Suppress("DEPRECATION")
    private fun loadGenreMap(): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        try {
            val genreUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            val genreProjection = arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME
            )
            context.contentResolver.query(genreUri, genreProjection, null, null, null)?.use { genreCursor ->
                val genreIdCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val genreNameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genreCursor.moveToNext()) {
                    val genreId = genreCursor.getLong(genreIdCol)
                    val genreName = genreCursor.getString(genreNameCol) ?: continue

                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    val membersProjection = arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    context.contentResolver.query(membersUri, membersProjection, null, null, null)?.use { membersCursor ->
                        val audioIdCol = membersCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (membersCursor.moveToNext()) {
                            val audioId = membersCursor.getLong(audioIdCol)
                            map[audioId] = genreName
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Genre query not supported on some devices
        }
        return map
    }

    private fun scanCustomFolders(
        folderPaths: List<String>,
        folderUris: Map<String, String>,
        songs: MutableList<Song>
    ) {
        val existingPaths = songs.map { it.filePath }.toSet()
        val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "aac", "wma", "opus")
        for (path in folderPaths) {
            val rootFolder = File(path)
            val canAccessViaFile = rootFolder.exists() && rootFolder.isDirectory
                    && (rootFolder.listFiles() != null)

            if (canAccessViaFile) {
                // File API works — use it
                rootFolder.walk().filter { it.isFile && it.extension.lowercase() in audioExtensions }.forEach { file ->
                    if (file.absolutePath in existingPaths) return@forEach

                    val folderPath = file.parent ?: ""
                    val folderName = File(folderPath).name

                    songs.add(Song(
                        id = -(file.absolutePath.hashCode().toLong() and 0x7FFFFFFF) - 1,
                        title = file.nameWithoutExtension,
                        artist = "Unknown Artist",
                        album = "Unknown Album",
                        albumId = 0L,
                        duration = 0L,
                        trackNumber = 0,
                        year = 0,
                        genre = "",
                        folderPath = folderPath,
                        folderName = folderName,
                        filePath = file.absolutePath,
                        fileName = file.name,
                        size = file.length(),
                        dateAdded = file.lastModified() / 1000,
                        dateModified = file.lastModified() / 1000,
                        uri = Uri.fromFile(file),
                        albumArtUri = null
                    ))
                }
            } else {
                // File API failed — fall back to SAF DocumentFile
                val treeUriString = folderUris[path] ?: continue
                val treeUri = Uri.parse(treeUriString)
                val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: continue
                scanDocumentFileRecursive(docFile, path, audioExtensions, existingPaths, songs)
            }
        }
    }

    private fun scanDocumentFileRecursive(
        dir: DocumentFile,
        rootPath: String,
        audioExtensions: Set<String>,
        existingPaths: Set<String>,
        songs: MutableList<Song>
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                scanDocumentFileRecursive(child, rootPath, audioExtensions, existingPaths, songs)
            } else if (child.isFile) {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in audioExtensions) continue
                val uri = child.uri
                if (uri.toString() in existingPaths) continue

                val folderName = dir.name ?: File(rootPath).name

                songs.add(Song(
                    id = -(uri.toString().hashCode().toLong() and 0x7FFFFFFF) - 1,
                    title = name.substringBeforeLast('.'),
                    artist = "Unknown Artist",
                    album = "Unknown Album",
                    albumId = 0L,
                    duration = 0L,
                    trackNumber = 0,
                    year = 0,
                    genre = "",
                    folderPath = rootPath,
                    folderName = folderName,
                    filePath = uri.toString(),
                    fileName = name,
                    size = child.length(),
                    dateAdded = child.lastModified() / 1000,
                    dateModified = child.lastModified() / 1000,
                    uri = uri,
                    albumArtUri = null
                ))
            }
        }
    }
}
