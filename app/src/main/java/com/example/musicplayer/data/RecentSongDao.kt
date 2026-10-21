package com.example.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RecentSongDao {
    @Insert
    suspend fun insertRecent(song: RecentSong)

    @Query("SELECT * FROM recent_songs ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentSongs(): List<RecentSong>
}
