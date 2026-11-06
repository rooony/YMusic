package com.example.musicplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteSong::class,
        RecentSong::class,
        ExcludedFolder::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SongDatabase : RoomDatabase() {
    abstract fun favoriteSongDao(): FavoriteSongDao
    abstract fun recentSongDao(): RecentSongDao
    abstract fun excludedFolderDao(): ExcludedFolderDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Songs are no longer persisted; they are rescanned from disk on
                // every launch. Drop the table but keep favorites, recents and
                // excluded folders.
                db.execSQL("DROP TABLE IF EXISTS songs")
            }
        }

        @Volatile
        private var INSTANCE: SongDatabase? = null

        fun getInstance(context: Context): SongDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SongDatabase::class.java,
                    "music_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
