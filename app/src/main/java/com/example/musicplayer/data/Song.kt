package com.example.musicplayer.data

/**
 * A song scanned from the device's media store. Songs are not persisted in the
 * database: the full list is rescanned from disk on every app launch and lives
 * only in memory for the current session.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val path: String = "",
    val albumArtUri: String? = null
)
