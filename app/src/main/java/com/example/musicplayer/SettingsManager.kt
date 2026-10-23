package com.example.musicplayer

import android.content.Context

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("ymusic_settings", Context.MODE_PRIVATE)

    fun saveVolume(volume: Float) {
        prefs.edit().putFloat("volume", volume).apply()
    }

    fun getVolume(): Float = prefs.getFloat("volume", 1f)

    fun saveAutoPlay(value: Boolean) {
        prefs.edit().putBoolean("auto_play", value).apply()
    }

    fun getAutoPlay(): Boolean = prefs.getBoolean("auto_play", true)

    fun saveShowAlbumArt(value: Boolean) {
        prefs.edit().putBoolean("show_album_art", value).apply()
    }

    fun getShowAlbumArt(): Boolean = prefs.getBoolean("show_album_art", false)

    fun saveThemeColor(color: Int) {
        prefs.edit().putInt("theme_color", color).apply()
    }

    fun getThemeColor(): Int = prefs.getInt("theme_color", 0xFF4F46E5.toInt())

    fun saveDarkMode(value: Boolean) {
        prefs.edit().putBoolean("dark_mode", value).apply()
    }

    fun getDarkMode(): Boolean = prefs.getBoolean("dark_mode", true)

    fun saveLanguage(language: String) {
        prefs.edit().putString("language", language).apply()
    }

    fun getLanguage(): String = prefs.getString("language", LanguageManager.LANG_ZH) ?: LanguageManager.LANG_ZH
}
