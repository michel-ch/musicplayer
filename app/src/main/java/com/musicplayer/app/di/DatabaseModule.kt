package com.musicplayer.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.musicplayer.app.data.local.db.CachedSongDao
import com.musicplayer.app.data.local.db.FavoriteDao
import com.musicplayer.app.data.local.db.MusicDatabase
import com.musicplayer.app.data.local.db.PlaylistDao
import com.musicplayer.app.data.local.db.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `search_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`query` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_songs` (" +
                    "`id` INTEGER PRIMARY KEY NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`artist` TEXT NOT NULL, " +
                    "`album` TEXT NOT NULL, " +
                    "`albumId` INTEGER NOT NULL, " +
                    "`duration` INTEGER NOT NULL, " +
                    "`trackNumber` INTEGER NOT NULL, " +
                    "`discNumber` INTEGER NOT NULL, " +
                    "`year` INTEGER NOT NULL, " +
                    "`genre` TEXT NOT NULL, " +
                    "`folderPath` TEXT NOT NULL, " +
                    "`folderName` TEXT NOT NULL, " +
                    "`filePath` TEXT NOT NULL, " +
                    "`fileName` TEXT NOT NULL, " +
                    "`size` INTEGER NOT NULL, " +
                    "`dateAdded` INTEGER NOT NULL, " +
                    "`dateModified` INTEGER NOT NULL, " +
                    "`uri` TEXT NOT NULL, " +
                    "`albumArtUri` TEXT, " +
                    "`composer` TEXT NOT NULL)"
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): MusicDatabase = Room.databaseBuilder(
        context,
        MusicDatabase::class.java,
        "music_player.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    @Provides
    fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideFavoriteDao(db: MusicDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideSearchHistoryDao(db: MusicDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideCachedSongDao(db: MusicDatabase): CachedSongDao = db.cachedSongDao()
}
