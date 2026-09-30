package dev.sphc.eafcon.presets

data class PositionPreset(
    val id: String,
    val name: String,
    val position: Int,
)

object PresetRules {
    const val MAX_NAME_LENGTH = 80
    const val MAX_PRESETS = 500
    const val MAX_FILE_BYTES = 1_000_000

    fun validateName(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Preset name cannot be empty" }
        require(normalized.length <= MAX_NAME_LENGTH) { "Preset name must be $MAX_NAME_LENGTH characters or fewer" }
        return normalized
    }

    fun validatePosition(position: Int, maximum: Int? = null, deviceMaximum: Int? = null): Int {
        require(position >= 0) { "Preset position cannot be negative" }
        maximum?.let { require(position <= it) { "Preset position exceeds the software safety maximum ($it)" } }
        deviceMaximum?.let { require(position <= it) { "Preset position exceeds the device-reported maximum ($it)" } }
        return position
    }

    fun merge(existing: List<PositionPreset>, incoming: List<PositionPreset>): List<PositionPreset> {
        val merged = existing.toMutableList()
        incoming.forEach { candidate ->
            val existingIndex = merged.indexOfFirst { it.id == candidate.id }
            if (existingIndex >= 0) merged[existingIndex] = candidate else merged += candidate
        }
        require(merged.size <= MAX_PRESETS) { "Import would exceed $MAX_PRESETS presets" }
        return merged
    }
}
