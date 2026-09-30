package dev.sphc.eafcon.presets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PresetJsonCodecTest {
    @Test
    fun roundTripPreservesUnicodeAndPortableFields() {
        val presets = listOf(
            PositionPreset("id-1", "90GT Visual", 5100),
            PositionPreset("id-2", "달 촬영 · SV106", 8355),
        )

        val encoded = PresetJsonCodec.encode(presets)

        assertEquals(presets, PresetJsonCodec.decode(encoded))
        assertEquals(true, encoded.contains("\"version\": 1"))
        assertEquals(false, encoded.contains("deviceName"))
    }

    @Test
    fun acceptsEmptyPresetArray() {
        assertEquals(emptyList<PositionPreset>(), PresetJsonCodec.decode("""{"format":"EAFCon Focuser Presets","version":1,"presets":[]}"""))
    }

    @Test
    fun rejectsInvalidFilesAndPositions() {
        assertThrows(PresetFileException::class.java) { PresetJsonCodec.decode("not json") }
        assertThrows(PresetFileException::class.java) {
            PresetJsonCodec.decode("""{"format":"Other","version":1,"presets":[]}""")
        }
        assertThrows(PresetFileException::class.java) {
            PresetJsonCodec.decode("""{"format":"EAFCon Focuser Presets","version":2,"presets":[]}""")
        }
        assertThrows(PresetFileException::class.java) {
            PresetJsonCodec.decode("""{"format":"EAFCon Focuser Presets","version":1,"presets":[{"id":"x","name":"test","position":-1}]}""")
        }
        assertThrows(PresetFileException::class.java) {
            PresetJsonCodec.decode("""{"format":"EAFCon Focuser Presets","version":1,"presets":[{"id":"x","name":"test","position":"5"}]}""")
        }
        assertThrows(PresetFileException::class.java) {
            PresetJsonCodec.decode("""{"format":"EAFCon Focuser Presets","version":1,"presets":[{"id":"x","name":"  ","position":5}]}""")
        }
    }

    @Test
    fun rejectsDuplicateIdsButAllowsDuplicateNamesWithDifferentIds() {
        val duplicateIds = """{"format":"EAFCon Focuser Presets","version":1,"presets":[{"id":"x","name":"A","position":5},{"id":"x","name":"B","position":6}]}"""
        assertThrows(PresetFileException::class.java) { PresetJsonCodec.decode(duplicateIds) }

        val duplicateNames = listOf(PositionPreset("x", "A", 5), PositionPreset("y", "A", 6))
        assertEquals(duplicateNames, PresetJsonCodec.decode(PresetJsonCodec.encode(duplicateNames)))
    }

    @Test
    fun validationAndMergeAreDeterministic() {
        assertThrows(IllegalArgumentException::class.java) { PresetRules.validatePosition(100, maximum = 99) }
        assertThrows(IllegalArgumentException::class.java) { PresetRules.validatePosition(10, deviceMaximum = 9) }
        assertEquals("안시 관측", PresetRules.validateName("  안시 관측  "))

        val merged = PresetRules.merge(
            listOf(PositionPreset("same", "Old", 5), PositionPreset("keep", "Keep", 10)),
            listOf(PositionPreset("same", "Updated", 6), PositionPreset("new", "New", 7)),
        )
        assertEquals(
            listOf(PositionPreset("same", "Updated", 6), PositionPreset("keep", "Keep", 10), PositionPreset("new", "New", 7)),
            merged,
        )
    }
}
