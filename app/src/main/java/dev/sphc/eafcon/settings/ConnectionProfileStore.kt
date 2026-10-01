package dev.sphc.eafcon.settings

import android.content.Context

class ConnectionProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ConnectionProfileCatalog {
        val stored = preferences.getString(KEY_CONNECTION_PROFILES, null)
        if (stored != null) {
            runCatching { ConnectionProfileJsonCodec.decode(stored) }.getOrNull()?.let { return it }
        }
        return loadBundledDefaults()
    }

    fun save(catalog: ConnectionProfileCatalog) {
        val json = ConnectionProfileJsonCodec.encode(catalog)
        preferences.edit().putString(KEY_CONNECTION_PROFILES, json).apply()
    }

    fun reset(): ConnectionProfileCatalog {
        val defaults = loadBundledDefaults()
        preferences.edit().remove(KEY_CONNECTION_PROFILES).apply()
        return defaults
    }

    private fun loadBundledDefaults(): ConnectionProfileCatalog {
        val json = appContext.assets.open(DEFAULT_ASSET).bufferedReader().use { it.readText() }
        return ConnectionProfileJsonCodec.decode(json)
    }

    private companion object {
        const val PREFERENCES_NAME = "eafcon_preferences"
        const val KEY_CONNECTION_PROFILES = "connection_profiles_v1"
        const val DEFAULT_ASSET = "connection_profiles.json"
    }
}
