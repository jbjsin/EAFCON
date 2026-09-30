package dev.sphc.eafcon.presets

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object PresetJsonCodec {
    const val FORMAT = "EAFCon Focuser Presets"
    const val VERSION = 1

    fun encode(presets: List<PositionPreset>): String {
        require(presets.size <= PresetRules.MAX_PRESETS) { "A maximum of ${PresetRules.MAX_PRESETS} presets can be exported" }
        require(presets.map { it.id }.toSet().size == presets.size) { "Preset IDs must be unique" }
        val array = JSONArray()
        presets.forEach { preset ->
            val name = PresetRules.validateName(preset.name)
            PresetRules.validatePosition(preset.position)
            require(preset.id.isNotBlank() && preset.id.length <= MAX_ID_LENGTH) { "Preset ID is invalid" }
            array.put(JSONObject()
                .put("id", preset.id)
                .put("name", name)
                .put("position", preset.position))
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("presets", array)
            .toString(2)
    }

    @Throws(PresetFileException::class)
    fun decode(json: String): List<PositionPreset> {
        if (json.toByteArray(Charsets.UTF_8).size > PresetRules.MAX_FILE_BYTES) {
            throw PresetFileException("Preset file is too large")
        }
        try {
            val root = JSONObject(json)
            if (root.opt("format") != FORMAT) throw PresetFileException("This is not an EAFCon preset file")
            val version = root.opt("version") as? Int
                ?: throw PresetFileException("Preset file version is missing or invalid")
            if (version != VERSION) {
                throw PresetFileException("Preset file version $version is not supported (supported: $VERSION)")
            }
            val array = root.opt("presets") as? JSONArray
                ?: throw PresetFileException("Preset file is missing the presets array")
            if (array.length() > PresetRules.MAX_PRESETS) {
                throw PresetFileException("A maximum of ${PresetRules.MAX_PRESETS} presets can be imported")
            }
            val result = ArrayList<PositionPreset>(array.length())
            val ids = HashSet<String>()
            for (index in 0 until array.length()) {
                val item = array.opt(index) as? JSONObject
                    ?: throw PresetFileException("Preset ${index + 1} is not an object")
                val id = item.opt("id") as? String
                    ?: throw PresetFileException("Preset ${index + 1} has an invalid id")
                if (id.isBlank() || id.length > MAX_ID_LENGTH) {
                    throw PresetFileException("Preset ${index + 1} has an invalid id")
                }
                if (!ids.add(id)) throw PresetFileException("Preset file contains duplicate id '$id'")
                val name = item.opt("name") as? String
                    ?: throw PresetFileException("Preset ${index + 1} has an invalid name")
                val positionValue = item.opt("position") as? Number
                    ?: throw PresetFileException("Preset '${name.take(MAX_ERROR_NAME_LENGTH)}' has an invalid position")
                val longPosition = positionValue.toLong()
                if (positionValue.toDouble() != longPosition.toDouble() || longPosition !in 0..Int.MAX_VALUE.toLong()) {
                    throw PresetFileException("Preset '${name.take(MAX_ERROR_NAME_LENGTH)}' has an invalid position")
                }
                val normalizedName = try {
                    PresetRules.validateName(name)
                } catch (error: IllegalArgumentException) {
                    throw PresetFileException("Preset ${index + 1}: ${error.message}")
                }
                result += PositionPreset(id, normalizedName, longPosition.toInt())
            }
            return result
        } catch (error: PresetFileException) {
            throw error
        } catch (error: JSONException) {
            throw PresetFileException("Preset file contains invalid JSON")
        } catch (error: RuntimeException) {
            throw PresetFileException("Preset file is malformed")
        }
    }

    private const val MAX_ID_LENGTH = 128
    private const val MAX_ERROR_NAME_LENGTH = 40
}

class PresetFileException(message: String) : Exception(message)
