package dev.sphc.eafcon.settings

enum class TemperatureDisplayUnit {
    CELSIUS,
    FAHRENHEIT,

    ;

    val degreeSymbol: String
        get() = if (this == CELSIUS) "°C" else "°F"

    companion object {
        fun fromControllerValue(value: String): TemperatureDisplayUnit? = when (value) {
            "CELSIUS" -> CELSIUS
            "FAHRENHEIT" -> FAHRENHEIT
            else -> null
        }
    }
}

fun formatTemperature(celsius: Double?, unit: TemperatureDisplayUnit): String {
    if (celsius == null) return if (unit == TemperatureDisplayUnit.CELSIUS) "— °C" else "— °F"
    val value = if (unit == TemperatureDisplayUnit.CELSIUS) celsius else celsius * 9.0 / 5.0 + 32.0
    return "${"%.1f".format(java.util.Locale.US, value)} ${unit.degreeSymbol}"
}
