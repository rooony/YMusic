package com.example.musicplayer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaylistManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("ymusic_playlists", Context.MODE_PRIVATE)

    fun savePlaylists(playlists: List<Playlist>) {
        val json = Gson().toJson(playlists)
        prefs.edit().putString("playlists", json).apply()
    }

    fun loadPlaylists(): List<Playlist> {
        val json = prefs.getString("playlists", null) ?: return emptyList()
        val type = object : TypeToken<List<Playlist>>() {}.type
        return Gson().fromJson(json, type)
    }
}
