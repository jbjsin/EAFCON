package dev.sphc.eafcon.protocol

import dev.sphc.eafcon.control.MovementLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiProtocolTest {
    @Test fun parsesKnownResponses() {
        assertEquals(GeminiResponse.Handshake, GeminiProtocol.parseResponse("EOK#"))
        assertEquals(GeminiResponse.Position(7500), GeminiProtocol.parseResponse("P7500#"))
        assertEquals(GeminiResponse.Movement(false), GeminiProtocol.parseResponse("I0#"))
        assertEquals(GeminiResponse.Movement(true), GeminiProtocol.parseResponse("I1#"))
        assertEquals(GeminiResponse.Temperature(18.0), GeminiProtocol.parseResponse("Z18.00#"))
        assertEquals(GeminiResponse.MaximumPosition(15000), GeminiProtocol.parseResponse("M15000#"))
    }

    @Test fun parserHandlesFragmentedAndCoalescedResponses() {
        val parser = DelimitedResponseParser()
        assertEquals(emptyList<String>(), parser.append("P75"))
        assertEquals(listOf("P7500#"), parser.append("00#"))
        assertEquals(listOf("I0#", "Z18.00#"), parser.append("I0#Z18.00#"))
    }

    @Test fun generatesCanonicalAbsoluteMove() {
        assertEquals(":057510#", GeminiProtocol.moveAbsolute(7510))
        assertThrows(IllegalArgumentException::class.java) { GeminiProtocol.moveAbsolute(-1) }
    }

    @Test fun stopCommandUsesMyFocuserPro2HaltFrame() {
        assertEquals(":27#", GeminiProtocol.STOP)
    }

    @Test fun relativeTargetsRespectSoftwareLimits() {
        assertEquals(7525, MovementLimits.relativeTarget(7500, 25, 15000))
        assertEquals(7475, MovementLimits.relativeTarget(7500, -25, 15000))
        assertThrows(IllegalArgumentException::class.java) { MovementLimits.relativeTarget(5, -25, 15000) }
        assertThrows(IllegalArgumentException::class.java) { MovementLimits.relativeTarget(14995, 25, 15000) }
        assertThrows(IllegalArgumentException::class.java) { MovementLimits.relativeTarget(null, 25, 15000) }
        assertThrows(IllegalArgumentException::class.java) { MovementLimits.validateAbsolute(15001, 15000) }
    }

    @Test fun rejectsMalformedOrUnsupportedFrames() {
        assertThrows(IllegalArgumentException::class.java) { GeminiProtocol.parseResponse("P7500") }
        assertThrows(IllegalArgumentException::class.java) { GeminiProtocol.parseResponse("Pbad#") }
        assertThrows(IllegalArgumentException::class.java) { GeminiProtocol.parseResponse("I2#") }
        assertThrows(IllegalArgumentException::class.java) { GeminiProtocol.parseResponse("ZNaN#") }
        assertThrows(IllegalArgumentException::class.java) { GeminiProtocol.parseResponse("X1#") }
    }

    @Test fun parserRecoversAfterOversizedFrameAndReset() {
        val parser = DelimitedResponseParser(maxFrameLength = 4)
        assertThrows(IllegalArgumentException::class.java) { parser.append("12345") }
        parser.reset()
        assertEquals(listOf("I0#"), parser.append("I0#"))
    }
}
