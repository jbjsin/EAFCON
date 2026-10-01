package dev.sphc.eafcon.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppLanguage(val languageTag: String) {
    ENGLISH("en"),
    KOREAN("ko"),
}

class LanguagePreferenceStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppLanguage = runCatching {
        AppLanguage.valueOf(preferences.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.name)!!)
    }.getOrDefault(AppLanguage.ENGLISH)

    fun save(language: AppLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "eafcon_preferences"
        const val KEY_LANGUAGE = "app_language_v1"
    }
}

fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = Locale.forLanguageTag(language.languageTag)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}
