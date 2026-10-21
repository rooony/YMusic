package com.example.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.flow.Flow

class SongRepository(
    private val context: Context,
    private val database: SongDatabase
) {
    private val dao = database.songDao()
    private val favoriteDao = database.favoriteSongDao()
    private val recentDao = database.recentSongDao()
    private val excludedFolderDao = database.excludedFolderDao()

    fun getAllSongs(): Flow<List<Song>> = dao.getAllSongs()

    suspend fun scanAndSaveSongs(): List<Song> {
        val excludedFolders = getExcludedFolders()
        val songs = readLocalSongs(excludedFolders)
        dao.clearAll()
        dao.insertSongs(songs)
        return songs
    }

    suspend fun addExcludedFolder(path: String) {
        val clean = normalizePath(path)
        if (clean.isNotBlank()) {
            excludedFolderDao.addFolder(ExcludedFolder(clean))
        }
    }

    suspend fun removeExcludedFolder(path: String) {
        excludedFolderDao.removeFolder(normalizePath(path))
    }

    suspend fun getExcludedFolders(): List<String> {
        return excludedFolderDao.getFolders().map { it.path }
    }

    suspend fun getDefaultExcludedFolders(): List<String> {
        val root = "/storage/emulated/0"
        return listOf(
            "$root/Android",
            "$root/Download"
        )
    }

    suspend fun addDefaultExcludedFoldersIfMissing() {
        val defaults = getDefaultExcludedFolders()
        val existing = getExcludedFolders().toSet()
        for (dir in defaults) {
            if (dir !in existing) {
                excludedFolderDao.addFolder(ExcludedFolder(dir))
            }
        }
    }

    suspend fun toggleFavorite(songId: Long) {
        val isFavorite = favoriteDao.isFavorite(songId)
        if (isFavorite) {
            favoriteDao.removeFavorite(songId)
        } else {
            favoriteDao.insertFavorite(FavoriteSong(songId))
        }
    }

    suspend fun isFavorite(songId: Long): Boolean {
        return favoriteDao.isFavorite(songId)
    }

    suspend fun getFavoriteSongIds(): List<Long> {
        return favoriteDao.getFavoriteIds()
    }

    suspend fun addRecentPlay(song: Song) {
        recentDao.insertRecent(
            RecentSong(
                songId = song.id,
                title = song.title,
                artist = song.artist
            )
        )
    }

    suspend fun getRecentSongs(): List<RecentSong> {
        return recentDao.getRecentSongs()
    }

    private fun readLocalSongs(excludedFolders: List<String>): List<Song> {
        val result = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val cursor = context.contentResolver.query(
            uri,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DEFAULT_SORT_ORDER} ASC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn) ?: "Unknown"
                val artist = it.getString(artistColumn) ?: "Unknown Artist"
                val album = it.getString(albumColumn) ?: "Unknown Album"
                val duration = it.getLong(durationColumn)
                val path = it.getString(dataColumn) ?: ""

                if (isInExcludedFolder(path, excludedFolders)) {
                    continue
                }

                val songUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                result.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = songUri.toString(),
                        path = path
                    )
                )
            }
        }

        return result
    }

    private fun isInExcludedFolder(songPath: String, excludedFolders: List<String>): Boolean {
        if (songPath.isBlank()) return false
        val normalizedSongPath = normalizePath(songPath)
        return excludedFolders.any { excluded ->
            val normalizedExcluded = normalizePath(excluded)
            normalizedSongPath.startsWith(normalizedExcluded, ignoreCase = true)
        }
    }

    private fun normalizePath(path: String): String {
        return path.replace("\\", "/").trim().trimEnd('/')
    }
}
