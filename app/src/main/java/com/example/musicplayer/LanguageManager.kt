package com.example.musicplayer

import android.content.Context
import java.util.Locale

object LanguageManager {
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    fun apply(context: Context) {
        val settings = SettingsManager(context)
        val lang = settings.getLanguage()
        val locale = Locale(lang)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getDisplayLanguage(language: String): String {
        return when (language) {
            LANG_ZH -> "中文"
            LANG_EN -> "English"
            else -> "中文"
        }
    }
}
