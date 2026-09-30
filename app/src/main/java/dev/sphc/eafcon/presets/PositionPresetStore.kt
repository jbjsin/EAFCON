package dev.sphc.eafcon.presets

import android.content.Context

class PositionPresetStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<PositionPreset> {
        val json = preferences.getString(KEY_PRESETS, null) ?: return emptyList()
        return runCatching { PresetJsonCodec.decode(json) }.getOrDefault(emptyList())
    }

    fun save(presets: List<PositionPreset>) {
        preferences.edit().putString(KEY_PRESETS, PresetJsonCodec.encode(presets)).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "eafcon_preferences"
        const val KEY_PRESETS = "position_presets_v1"
    }
}
