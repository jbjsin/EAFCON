package dev.sphc.eafcon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConnectionProfileJsonCodecTest {
    private val catalog = ConnectionProfileCatalog(
        defaultProfileId = "gemini",
        profiles = listOf(
            ConnectionProfile(
                id = "gemini",
                name = "Gemini / MyFocuserPro2",
                serial = SerialParameters(
                    baudRate = 9600,
                    dataBits = 8,
                    stopBits = SerialStopBits.ONE,
                    parity = SerialParity.NONE,
                    flowControl = SerialFlowControl.NONE,
                    readTimeoutMs = 250,
                    writeTimeoutMs = 1000,
                    responseTimeoutMs = 3000,
                ),
            ),
        ),
    )

    @Test
    fun roundTripPreservesVersionedProfiles() {
        assertEquals(catalog, ConnectionProfileJsonCodec.decode(ConnectionProfileJsonCodec.encode(catalog)))
    }

    @Test
    fun rejectsUnknownVersion() {
        val json = ConnectionProfileJsonCodec.encode(catalog).replace("\"version\": 1", "\"version\": 2")
        assertThrows(ConnectionProfileFileException::class.java) {
            ConnectionProfileJsonCodec.decode(json)
        }
    }

    @Test
    fun rejectsInvalidSerialValuesBeforeReturningCatalog() {
        val json = ConnectionProfileJsonCodec.encode(catalog).replace("\"dataBits\": 8", "\"dataBits\": 9")
        assertThrows(ConnectionProfileFileException::class.java) {
            ConnectionProfileJsonCodec.decode(json)
        }
    }

    @Test
    fun rejectsMissingDefaultProfile() {
        val json = ConnectionProfileJsonCodec.encode(catalog)
            .replace("\"defaultProfileId\": \"gemini\"", "\"defaultProfileId\": \"missing\"")
        assertThrows(ConnectionProfileFileException::class.java) {
            ConnectionProfileJsonCodec.decode(json)
        }
    }
}
