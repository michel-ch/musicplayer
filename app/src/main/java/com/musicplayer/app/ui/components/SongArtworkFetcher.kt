package com.musicplayer.app.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import okio.buffer
import okio.source

/**
 * Coil fetcher that extracts per-song embedded artwork using [MediaMetadataRetriever].
 * Falls back to the shared album art URI when no embedded picture is found.
 */
class SongArtworkFetcher(
    private val context: Context,
    private val data: SongArtModel,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Try embedded art from the audio file
        val retriever = MediaMetadataRetriever()
        try {
            // Prefer file path for reliable per-song art extraction
            if (data.filePath != null) {
                retriever.setDataSource(data.filePath)
            } else {
                retriever.setDataSource(context, data.songUri)
            }
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                return SourceResult(
                    source = ImageSource(
                        source = Buffer().apply { write(artBytes) },
                        context = context
                    ),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK
                )
            }
        } catch (_: Exception) {
            // File may not be accessible via this URI
        } finally {
            retriever.release()
        }

        // No embedded art found — show default icon (don't fall back to album art
        // which may belong to a different song in the same folder/album)
        error("No artwork available")
    }

    class Factory(private val context: Context) : Fetcher.Factory<SongArtModel> {
        override fun create(data: SongArtModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return SongArtworkFetcher(context, data)
        }
    }
}
