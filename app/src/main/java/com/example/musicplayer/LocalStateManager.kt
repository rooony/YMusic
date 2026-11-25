package com.example.musicplayer

import android.content.Context

class LocalStateManager(context: Context) {
    private val prefs = context.getSharedPreferences("ymusic_state", Context.MODE_PRIVATE)

    fun saveLastSongId(songId: Long) {
        prefs.edit().putLong("last_song_id", songId).apply()
    }

    fun getLastSongId(): Long = prefs.getLong("last_song_id", -1L)

    fun saveLastPosition(ms: Long) {
        prefs.edit().putLong("last_position_ms", ms).apply()
    }

    fun getLastPosition(): Long = prefs.getLong("last_position_ms", 0L)

    fun saveQueue(songIds: List<Long>) {
        prefs.edit().putString("queue_song_ids", songIds.joinToString(",")).apply()
    }

    fun getQueueSongIds(): List<Long> {
        val raw = prefs.getString("queue_song_ids", null) ?: return emptyList()
        return raw.split(',').mapNotNull { it.toLongOrNull() }
    }

    fun savePlayMode(mode: PlayMode) {
        prefs.edit().putString("play_mode", mode.name).apply()
    }

    fun getPlayMode(): PlayMode {
        val raw = prefs.getString("play_mode", PlayMode.LIST_LOOP.name)
        return try {
            PlayMode.valueOf(raw ?: PlayMode.LIST_LOOP.name)
        } catch (_: Exception) {
            PlayMode.LIST_LOOP
        }
    }
}
