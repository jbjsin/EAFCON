package dev.sphc.eafcon.settings

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ConnectionProfileJsonCodec {
    const val FORMAT = "EAFCon Connection Profiles"
    const val VERSION = 1

    fun encode(catalog: ConnectionProfileCatalog): String {
        ConnectionProfileRules.validate(catalog)
        val profiles = JSONArray()
        catalog.profiles.forEach { profile ->
            val serial = profile.serial
            profiles.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put(
                        "serial",
                        JSONObject()
                            .put("baudRate", serial.baudRate)
                            .put("dataBits", serial.dataBits)
                            .put("stopBits", serial.stopBits.name)
                            .put("parity", serial.parity.name)
                            .put("flowControl", serial.flowControl.name)
                            .put("readTimeoutMs", serial.readTimeoutMs)
                            .put("writeTimeoutMs", serial.writeTimeoutMs)
                            .put("responseTimeoutMs", serial.responseTimeoutMs),
                    ),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("defaultProfileId", catalog.defaultProfileId)
            .put("profiles", profiles)
            .toString(2)
    }

    @Throws(ConnectionProfileFileException::class)
    fun decode(json: String): ConnectionProfileCatalog {
        if (json.toByteArray(Charsets.UTF_8).size > ConnectionProfileRules.MAX_FILE_BYTES) {
            throw ConnectionProfileFileException("Connection profile file is too large")
        }
        try {
            val root = JSONObject(json)
            if (root.opt("format") != FORMAT) {
                throw ConnectionProfileFileException("This is not an EAFCon connection profile file")
            }
            val version = root.intValue("version", "Connection profile version is missing or invalid")
            if (version != VERSION) {
                throw ConnectionProfileFileException(
                    "Connection profile version $version is not supported (supported: $VERSION)",
                )
            }
            val defaultProfileId = root.stringValue("defaultProfileId")
            val array = root.opt("profiles") as? JSONArray
                ?: throw ConnectionProfileFileException("Connection profile file is missing the profiles array")
            if (array.length() > ConnectionProfileRules.MAX_PROFILES) {
                throw ConnectionProfileFileException("Connection profile file contains too many profiles")
            }
            val profiles = ArrayList<ConnectionProfile>(array.length())
            for (index in 0 until array.length()) {
                val item = array.opt(index) as? JSONObject
                    ?: throw ConnectionProfileFileException("Connection profile ${index + 1} is not an object")
                val serial = item.opt("serial") as? JSONObject
                    ?: throw ConnectionProfileFileException("Connection profile ${index + 1} has no serial settings")
                profiles += ConnectionProfile(
                    id = item.stringValue("id"),
                    name = item.stringValue("name"),
                    serial = SerialParameters(
                        baudRate = serial.intValue("baudRate"),
                        dataBits = serial.intValue("dataBits"),
                        stopBits = serial.enumValue("stopBits"),
                        parity = serial.enumValue("parity"),
                        flowControl = serial.enumValue("flowControl"),
                        readTimeoutMs = serial.intValue("readTimeoutMs"),
                        writeTimeoutMs = serial.intValue("writeTimeoutMs"),
                        responseTimeoutMs = serial.intValue("responseTimeoutMs"),
                    ),
                )
            }
            return try {
                ConnectionProfileRules.validate(ConnectionProfileCatalog(defaultProfileId, profiles))
            } catch (error: IllegalArgumentException) {
                throw ConnectionProfileFileException(error.message ?: "Connection profile file is invalid")
            } catch (error: IllegalStateException) {
                throw ConnectionProfileFileException(error.message ?: "Connection profile file is invalid")
            }
        } catch (error: ConnectionProfileFileException) {
            throw error
        } catch (error: JSONException) {
            throw ConnectionProfileFileException("Connection profile file contains invalid JSON")
        } catch (error: RuntimeException) {
            throw ConnectionProfileFileException("Connection profile file is malformed")
        }
    }

    private fun JSONObject.stringValue(key: String): String = opt(key) as? String
        ?: throw ConnectionProfileFileException("Connection profile field '$key' is missing or invalid")

    private fun JSONObject.intValue(key: String, message: String = "Connection profile field '$key' is missing or invalid"): Int {
        val value = opt(key) as? Number ?: throw ConnectionProfileFileException(message)
        val longValue = value.toLong()
        if (value.toDouble() != longValue.toDouble() || longValue !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            throw ConnectionProfileFileException(message)
        }
        return longValue.toInt()
    }

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(key: String): T {
        val value = stringValue(key)
        return enumValues<T>().firstOrNull { it.name == value }
            ?: throw ConnectionProfileFileException("Connection profile field '$key' has unsupported value '$value'")
    }
}

class ConnectionProfileFileException(message: String) : Exception(message)
