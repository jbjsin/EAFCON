package dev.sphc.eafcon.presets

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class PositionPresetStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val storage = PresetFileStorage(File(applicationContext.filesDir, FILE_NAME))

    fun load(): List<PositionPreset> {
        val legacyJson = preferences.getString(KEY_PRESETS, null)
        val legacyPresets = legacyJson?.let { runCatching { PresetJsonCodec.decode(it) }.getOrNull() }
        val presets = storage.loadOrCreate(legacyPresets ?: DEFAULT_PRESETS)
        if (legacyJson != null) preferences.edit().remove(KEY_PRESETS).apply()
        return presets
    }

    fun save(presets: List<PositionPreset>) {
        storage.save(presets)
    }

    companion object {
        const val FILE_NAME = "Preset.json"
        internal const val DEFAULT_PRESET_ID = "default-system"
        internal val DEFAULT_PRESETS = listOf(
            PositionPreset(DEFAULT_PRESET_ID, "Default System", 7500),
        )

        const val PREFERENCES_NAME = "eafcon_preferences"
        const val KEY_PRESETS = "position_presets_v1"
    }
}

internal class PresetFileStorage(private val file: File) {
    @Synchronized
    fun loadOrCreate(initialPresets: List<PositionPreset>): List<PositionPreset> {
        if (!file.exists()) writeAtomically(initialPresets)
        return load()
    }

    @Synchronized
    fun load(): List<PositionPreset> = PresetJsonCodec.decode(file.readText(StandardCharsets.UTF_8))

    @Synchronized
    fun save(presets: List<PositionPreset>) {
        writeAtomically(presets)
    }

    private fun writeAtomically(presets: List<PositionPreset>) {
        val json = PresetJsonCodec.encode(presets)
        val parent = requireNotNull(file.parentFile) { "Preset storage directory is unavailable" }
        check(parent.isDirectory || parent.mkdirs()) { "Unable to create the preset storage directory" }
        val temporaryFile = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(json.toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            temporaryFile.delete()
        }
    }
}
