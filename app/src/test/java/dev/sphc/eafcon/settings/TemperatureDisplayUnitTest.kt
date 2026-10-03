package dev.sphc.eafcon.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureDisplayUnitTest {
    @Test fun formatsCelsiusAndFahrenheitFromTheSameSensorReading() {
        assertEquals("0.0 °C", formatTemperature(0.0, TemperatureDisplayUnit.CELSIUS))
        assertEquals("32.0 °F", formatTemperature(0.0, TemperatureDisplayUnit.FAHRENHEIT))
        assertEquals("18.0 °C", formatTemperature(18.0, TemperatureDisplayUnit.CELSIUS))
        assertEquals("64.4 °F", formatTemperature(18.0, TemperatureDisplayUnit.FAHRENHEIT))
    }

    @Test fun unknownReadingKeepsTheSelectedUnit() {
        assertEquals("— °C", formatTemperature(null, TemperatureDisplayUnit.CELSIUS))
        assertEquals("— °F", formatTemperature(null, TemperatureDisplayUnit.FAHRENHEIT))
    }

    @Test fun mapsControllerUnitValuesAndDegreeSymbols() {
        assertEquals(TemperatureDisplayUnit.CELSIUS, TemperatureDisplayUnit.fromControllerValue("CELSIUS"))
        assertEquals(TemperatureDisplayUnit.FAHRENHEIT, TemperatureDisplayUnit.fromControllerValue("FAHRENHEIT"))
        assertEquals(null, TemperatureDisplayUnit.fromControllerValue("KELVIN"))
        assertEquals("°C", TemperatureDisplayUnit.CELSIUS.degreeSymbol)
        assertEquals("°F", TemperatureDisplayUnit.FAHRENHEIT.degreeSymbol)
    }
}
