package dev.sphc.eafcon.driver

/** User-selected protocol/device profile. Neither value is inferred from VID/PID. */
enum class FocuserType(val persistentId: String, val displayName: String) {
    GEMINI_FOCUSER_PRO("gemini-focuser-pro", "Gemini Focuser Pro"),
    MYFOCUSERPRO2_GENERIC("myfocuserpro2-generic", "MyFocuserPro2 Generic");

    companion object {
        fun fromPersistentId(value: String?): FocuserType = entries.firstOrNull { it.persistentId == value }
            ?: GEMINI_FOCUSER_PRO
    }
}
