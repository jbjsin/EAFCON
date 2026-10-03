package dev.sphc.eafcon.settings

import android.content.Context
import dev.sphc.eafcon.driver.FocuserType

class FocuserTypeStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("eafcon_device_profile", Context.MODE_PRIVATE)

    fun load(): FocuserType = FocuserType.fromPersistentId(preferences.getString(KEY, null))

    fun save(type: FocuserType) {
        check(preferences.edit().putString(KEY, type.persistentId).commit()) {
            "Unable to save the focuser type"
        }
    }

    private companion object { const val KEY = "selected_focuser_type_v1" }
}
