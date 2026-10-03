package dev.sphc.eafcon.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MyFocuserPro2ProtocolTest {
    @Test fun encodesAdvancedReadsAndWritesCanonically() {
        assertEquals(":13#", MyFocuserPro2Protocol.readReverse())
        assertEquals(":140#", MyFocuserPro2Protocol.setReverse(false))
        assertEquals(":141#", MyFocuserPro2Protocol.setReverse(true))
        assertEquals(":43#", MyFocuserPro2Protocol.readMotorSpeed())
        assertEquals(":1502#", MyFocuserPro2Protocol.setMotorSpeed(2))
        assertEquals(":78#", MyFocuserPro2Protocol.readBacklashInSteps())
        assertEquals(":770#", MyFocuserPro2Protocol.setBacklashInSteps(0))
        assertEquals(":731#", MyFocuserPro2Protocol.setBacklashInEnabled(true))
        assertEquals(":74#", MyFocuserPro2Protocol.readBacklashInEnabled())
        assertEquals(":79255#", MyFocuserPro2Protocol.setBacklashOutSteps(255))
        assertEquals(":76#", MyFocuserPro2Protocol.readBacklashOutEnabled())
        assertEquals(":751#", MyFocuserPro2Protocol.setBacklashOutEnabled(true))
    }

    @Test fun parsesAndRejectsAdvancedResponsesStrictly() {
        assertTrue(MyFocuserPro2Protocol.parseReverse("R1#"))
        assertFalse(MyFocuserPro2Protocol.parseReverse("R0#"))
        assertEquals(2, MyFocuserPro2Protocol.parseMotorSpeed("C2#"))
        assertEquals(255, MyFocuserPro2Protocol.parseBacklashInSteps("6255#"))
        assertTrue(MyFocuserPro2Protocol.parseBacklashInEnabled("41#"))
        assertEquals(12, MyFocuserPro2Protocol.parseBacklashOutSteps("712#"))
        assertFalse(MyFocuserPro2Protocol.parseBacklashOutEnabled("50#"))

        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.parseReverse("R2#") }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.parseMotorSpeed("C3#") }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.parseReverse("R1") }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.parseBacklashInSteps("6bad#") }
    }

    @Test fun rejectsUnboundedOrInvalidWritesBeforeEncoding() {
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setMotorSpeed(3) }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setBacklashOutSteps(-1) }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setBacklashOutSteps(256) }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setTemperatureCoefficient(-1) }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setStepMode(3) }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.syncPosition(1_000_000) }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setDeviceMaximum(0) }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setDeviceMaximum(1_000_000) }
    }

    @Test fun catalogsAdministrativeEncodersWithoutMakingThemUiOperations() {
        assertEquals(":308#", MyFocuserPro2Protocol.setStepMode(8))
        assertEquals(":311000#", MyFocuserPro2Protocol.syncPosition(1000))
        assertEquals(":07015000#", MyFocuserPro2Protocol.setDeviceMaximum(15000))
        assertEquals(":28#", MyFocuserPro2Protocol.home())
        assertEquals(":11#", MyFocuserPro2Protocol.readCoilPower())
        assertEquals(":120#", MyFocuserPro2Protocol.setCoilPower(false))
        assertEquals(":37#", MyFocuserPro2Protocol.readDisplayEnabled())
        assertEquals(":361#", MyFocuserPro2Protocol.setDisplayEnabled(true))
        assertEquals(":16#", MyFocuserPro2Protocol.setTemperatureDisplayCelsius())
        assertTrue(MyFocuserPro2Protocol.parseStepMode("S256#") == 256)
        assertTrue(MyFocuserPro2Protocol.parseCoilPower("O1#"))
        assertFalse(MyFocuserPro2Protocol.parseDisplayEnabled("D0#"))
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.parseStepMode("S3#") }
    }

    @Test fun catalogsOfficial338AndGeminiConsoleCommands() {
        assertEquals(":152#", MyFocuserPro2Protocol.setMotorSpeed(2, geminiConsoleCanonical = true))
        assertEquals(":17#", MyFocuserPro2Protocol.setTemperatureDisplayFahrenheit())
        assertEquals(":38#", MyFocuserPro2Protocol.readTemperatureDisplayUnit())
        assertFalse(MyFocuserPro2Protocol.parseTemperatureDisplayCelsius("b0#"))
        assertEquals(":2012#", MyFocuserPro2Protocol.setTemperatureResolution(12))
        assertEquals(12, MyFocuserPro2Protocol.parseTemperatureResolution("Q12#"))
        assertEquals(":71125#", MyFocuserPro2Protocol.setDelayAfterMove(125))
        assertEquals(125, MyFocuserPro2Protocol.parseDelayAfterMove("3125#"))
        assertEquals(":651#", MyFocuserPro2Protocol.setJogEnabled(true))
        assertEquals(":671#", MyFocuserPro2Protocol.setJogDirection(true))
        assertEquals(":48#", MyFocuserPro2Protocol.persistSettings())
        assertEquals(":40#", MyFocuserPro2Protocol.resetController())
        assertEquals(":42#", MyFocuserPro2Protocol.restoreDefaults())
        assertEquals(":92101#", MyFocuserPro2Protocol.setDisplayPageOptions("101"))
        assertEquals("101", MyFocuserPro2Protocol.parseDisplayPageOptions("l101#"))
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setTemperatureCoefficient(1001) }
        assertThrows(IllegalArgumentException::class.java) { MyFocuserPro2Protocol.setDelayAfterMove(256) }
    }
}
