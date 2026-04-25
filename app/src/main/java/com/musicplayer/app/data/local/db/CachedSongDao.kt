package com.musicplayer.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedSongDao {

    @Query("SELECT * FROM cached_songs")
    suspend fun getAll(): List<CachedSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<CachedSongEntity>)

    @Query("DELETE FROM cached_songs")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(songs: List<CachedSongEntity>) {
        clear()
        insertAll(songs)
    }
}
