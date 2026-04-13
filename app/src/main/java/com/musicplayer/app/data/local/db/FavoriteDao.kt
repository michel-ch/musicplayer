package com.musicplayer.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun getAllFavoriteIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavoriteSync(songId: Long): Boolean

    @Transaction
    suspend fun toggleFavorite(songId: Long) {
        if (isFavoriteSync(songId)) {
            removeFavorite(songId)
        } else {
            addFavorite(FavoriteEntity(songId = songId))
        }
    }
}
