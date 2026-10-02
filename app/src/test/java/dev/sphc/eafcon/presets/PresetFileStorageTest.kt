package dev.sphc.eafcon.presets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PresetFileStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun firstLoadCreatesPresetJsonWithDefaultSystem() {
        val presetFile = File(temporaryFolder.root, PositionPresetStore.FILE_NAME)
        val storage = PresetFileStorage(presetFile)

        val loaded = storage.loadOrCreate(PositionPresetStore.DEFAULT_PRESETS)

        assertTrue(presetFile.isFile)
        assertEquals("Preset.json", presetFile.name)
        assertEquals(
            listOf(PositionPreset("default-system", "Default System", 7500)),
            loaded,
        )
        assertEquals(loaded, PresetJsonCodec.decode(presetFile.readText()))
    }

    @Test
    fun saveReplacesThePrivateDocumentAndSurvivesReload() {
        val presetFile = File(temporaryFolder.root, PositionPresetStore.FILE_NAME)
        val storage = PresetFileStorage(presetFile)
        storage.loadOrCreate(PositionPresetStore.DEFAULT_PRESETS)
        val updated = PresetRules.merge(
            storage.load(),
            listOf(
                PositionPreset("default-system", "Default System", 7600),
                PositionPreset("new-system", "New System", 4200),
            ),
        )

        storage.save(updated)

        assertEquals(updated, PresetFileStorage(presetFile).load())
        assertEquals(false, File(temporaryFolder.root, "Preset.json.tmp").exists())
    }
}
