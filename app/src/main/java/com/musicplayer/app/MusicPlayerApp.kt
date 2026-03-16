package com.musicplayer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.musicplayer.app.ui.components.SongArtworkFetcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MusicPlayerApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SongArtworkFetcher.Factory(this@MusicPlayerApp))
            }
            .build()
    }
}
