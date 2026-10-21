package com.example.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExcludedFolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFolder(folder: ExcludedFolder)

    @Query("DELETE FROM excluded_folders WHERE path = :path")
    suspend fun removeFolder(path: String)

    @Query("SELECT * FROM excluded_folders ORDER BY path ASC")
    suspend fun getFolders(): List<ExcludedFolder>
}
