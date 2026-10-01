package dev.sphc.eafcon.settings

enum class SerialParity { NONE, ODD, EVEN, MARK, SPACE }

enum class SerialStopBits(val displayName: String) {
    ONE("1"),
    ONE_POINT_FIVE("1.5"),
    TWO("2"),
}

enum class SerialFlowControl(val displayName: String) {
    NONE("None"),
    RTS_CTS("RTS/CTS"),
    DTR_DSR("DTR/DSR"),
    XON_XOFF("XON/XOFF"),
    XON_XOFF_INLINE("XON/XOFF inline"),
}

data class SerialParameters(
    val baudRate: Int,
    val dataBits: Int,
    val stopBits: SerialStopBits,
    val parity: SerialParity,
    val flowControl: SerialFlowControl,
    val readTimeoutMs: Int,
    val writeTimeoutMs: Int,
    val responseTimeoutMs: Int,
)

data class ConnectionProfile(
    val id: String,
    val name: String,
    val serial: SerialParameters,
)

data class ConnectionProfileCatalog(
    val defaultProfileId: String,
    val profiles: List<ConnectionProfile>,
) {
    fun defaultProfile(): ConnectionProfile =
        profiles.firstOrNull { it.id == defaultProfileId }
            ?: error("Default connection profile '$defaultProfileId' does not exist")
}

object ConnectionProfileRules {
    const val MAX_PROFILES = 100
    const val MAX_FILE_BYTES = 256_000
    const val MAX_ID_LENGTH = 80
    const val MAX_NAME_LENGTH = 80

    fun validate(catalog: ConnectionProfileCatalog): ConnectionProfileCatalog {
        require(catalog.profiles.isNotEmpty()) { "At least one connection profile is required" }
        require(catalog.profiles.size <= MAX_PROFILES) { "Too many connection profiles" }
        require(catalog.profiles.map { it.id }.toSet().size == catalog.profiles.size) {
            "Connection profile IDs must be unique"
        }
        catalog.profiles.forEach(::validate)
        catalog.defaultProfile()
        return catalog
    }

    fun validate(profile: ConnectionProfile): ConnectionProfile {
        require(profile.id.isNotBlank() && profile.id.length <= MAX_ID_LENGTH) {
            "Connection profile ID is invalid"
        }
        require(profile.name.isNotBlank() && profile.name.length <= MAX_NAME_LENGTH) {
            "Connection profile name is invalid"
        }
        validate(profile.serial)
        return profile
    }

    fun validate(serial: SerialParameters): SerialParameters {
        require(serial.baudRate in 300..4_000_000) { "Baud rate must be between 300 and 4000000" }
        require(serial.dataBits in 5..8) { "Data bits must be between 5 and 8" }
        require(serial.readTimeoutMs in 10..60_000) { "Read timeout must be between 10 and 60000 ms" }
        require(serial.writeTimeoutMs in 10..60_000) { "Write timeout must be between 10 and 60000 ms" }
        require(serial.responseTimeoutMs in 100..120_000) { "Response timeout must be between 100 and 120000 ms" }
        require(serial.responseTimeoutMs >= serial.readTimeoutMs) {
            "Response timeout must be at least the read timeout"
        }
        return serial
    }
}
