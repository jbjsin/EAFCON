package dev.sphc.eafcon.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MovementVibrationTest {
    @Test
    fun `five levels use requested percentages and Android amplitudes`() {
        assertEquals(listOf(10, 30, 50, 70, 90), VibrationStrength.entries.map { it.percent })
        assertEquals(listOf(26, 77, 128, 179, 230), VibrationStrength.entries.map { it.amplitude })
        assertEquals(listOf(1, 2, 3, 4, 5), VibrationStrength.entries.map { it.level })
    }

    @Test
    fun `legacy strengths migrate to nearest five-level values`() {
        assertEquals(VibrationStrength.LEVEL_2, vibrationStrengthFromStored("LOW"))
        assertEquals(VibrationStrength.LEVEL_3, vibrationStrengthFromStored("MEDIUM"))
        assertEquals(VibrationStrength.LEVEL_5, vibrationStrengthFromStored("HIGH"))
    }

    @Test
    fun `missing or invalid strength uses middle level`() {
        assertEquals(VibrationStrength.LEVEL_3, vibrationStrengthFromStored(null))
        assertEquals(VibrationStrength.LEVEL_3, vibrationStrengthFromStored("INVALID"))
    }

    @Test
    fun `new stored levels restore exactly`() {
        VibrationStrength.entries.forEach { strength ->
            assertEquals(strength, vibrationStrengthFromStored(strength.name))
        }
    }

    @Test
    fun `inactive strength changes do not create a movement vibration effect key`() {
        val level1Off = MovementVibrationSettings(enabled = false, strength = VibrationStrength.LEVEL_1)
        val level5Off = MovementVibrationSettings(enabled = false, strength = VibrationStrength.LEVEL_5)
        val level3On = MovementVibrationSettings(enabled = true, strength = VibrationStrength.LEVEL_3)

        assertEquals(null, activeMovementVibrationStrength(moving = false, level1Off))
        assertEquals(null, activeMovementVibrationStrength(moving = false, level5Off))
        assertEquals(null, activeMovementVibrationStrength(moving = false, level3On))
        assertEquals(VibrationStrength.LEVEL_3, activeMovementVibrationStrength(moving = true, level3On))
    }
}
