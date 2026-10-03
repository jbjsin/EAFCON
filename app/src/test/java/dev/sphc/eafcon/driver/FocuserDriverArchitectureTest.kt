package dev.sphc.eafcon.driver

import dev.sphc.eafcon.control.FocuserController
import dev.sphc.eafcon.control.MovementState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import dev.sphc.eafcon.usb.FakeSerialTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FocuserDriverArchitectureTest {
    @Test fun geminiAndGenericAreDistinctProfilesWithoutChangingTransportContract() = runBlocking {
        val geminiTransport = FakeSerialTransport()
        val genericTransport = FakeSerialTransport()
        val gemini = MyFocuserPro2Driver(
            focuserType = FocuserType.GEMINI_FOCUSER_PRO,
            enableUnverifiedProtocolWrites = true,
        ) { geminiTransport }
        val generic = MyFocuserPro2Driver(
            focuserType = FocuserType.MYFOCUSERPRO2_GENERIC,
            enableUnverifiedProtocolWrites = true,
        ) { genericTransport }

        assertEquals("gemini-focuser-pro", gemini.descriptor.id)
        assertEquals("myfocuserpro2-generic", generic.descriptor.id)
        assertEquals(CapabilitySupport.SUPPORTED, gemini.descriptor.capabilities[CapabilityId.PERSIST_SETTINGS]?.support)
        assertEquals(null, gemini.descriptor.capabilities[CapabilityId.PERSIST_SETTINGS]?.unavailableReason)
        assertEquals(VerificationState.VERIFIED, gemini.descriptor.capabilities[CapabilityId.RESET_CONTROLLER]?.verification)
        assertEquals(VerificationState.VERIFIED, gemini.descriptor.capabilities[CapabilityId.RESTORE_DEFAULTS]?.verification)
        assertEquals(
            VerificationState.SOURCE_VERIFIED_HARDWARE_UNVERIFIED,
            generic.descriptor.capabilities[CapabilityId.RESTORE_DEFAULTS]?.verification,
        )

        gemini.connect()
        generic.connect()
        gemini.writeCapability(CapabilityId.MOTOR_SPEED, CapabilityValue.IntegerValue(2))
        generic.writeCapability(CapabilityId.MOTOR_SPEED, CapabilityValue.IntegerValue(2))
        assertTrue(geminiTransport.sentCommands.contains(":152#"))
        assertTrue(genericTransport.sentCommands.contains(":1502#"))
        gemini.disconnect()
        generic.disconnect()
    }

    @Test fun devOnlyDeviceAdministrationUsesManufacturerAndFirmwareBackedFrames() = runBlocking {
        val transport = FakeSerialTransport()
        val driver = MyFocuserPro2Driver(enableUnverifiedProtocolWrites = true) { transport }
        driver.connect()
        driver.writeCapability(CapabilityId.PERSIST_SETTINGS, CapabilityValue.TriggerValue)
        driver.writeCapability(CapabilityId.RESET_CONTROLLER, CapabilityValue.TriggerValue)
        driver.writeCapability(CapabilityId.RESTORE_DEFAULTS, CapabilityValue.TriggerValue)
        assertTrue(transport.sentCommands.containsAll(listOf(":48#", ":40#", ":42#")))
        driver.disconnect()
    }

    @Test fun controllerUsesTransportNeutralDriverWithoutSerialTypes() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = FocuserController(scope, pollIdleMs = 100, pollMovingMs = 50, pollTemperatureMs = 100)
        val driver = MinimalDriver()
        try {
            controller.connect(driver)
            assertTrue(controller.state.value.connected)
            assertEquals(1200, controller.state.value.currentPosition)
            controller.setSoftwareMaximum(2000)
            controller.moveTo(1300)
            assertEquals(1300, driver.target)
            assertEquals(MovementState.IDLE, controller.state.value.movement)
        } finally {
            controller.disconnect()
            scope.cancel()
        }
    }

    @Test fun unknownCapabilityMetadataIsPreservedAndNeverInvoked() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = FocuserController(scope, pollIdleMs = 100, pollMovingMs = 50, pollTemperatureMs = 100)
        val driver = UnknownCapabilityDriver()
        try {
            controller.connect(driver)
            val metadata = controller.capabilities?.get(CapabilityId.REVERSE)
            assertEquals(CapabilitySupport.UNKNOWN, metadata?.support)
            assertEquals(VerificationState.UNKNOWN, metadata?.verification)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { controller.readCapability(CapabilityId.REVERSE) }
            }
            assertEquals(0, driver.readCalls)
        } finally {
            controller.disconnect()
            scope.cancel()
        }
    }

    private class MinimalDriver : FocuserDriver {
        var target = 1200
        override val descriptor = FocuserDriverDescriptor("test-hid", "Test HID", CapabilitySet(emptyList()))
        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun readPosition() = target
        override suspend fun readMovementState() = MovementState.IDLE
        override suspend fun moveAbsolute(position: Int) { target = position }
        override suspend fun stop() = Unit
    }

    private class UnknownCapabilityDriver : FocuserDriver, CapabilitySettingsDriver {
        var readCalls = 0
        override val descriptor = FocuserDriverDescriptor(
            "unknown-test", "Unknown test driver", CapabilitySet(listOf(
                CapabilityDescriptor(
                    id = CapabilityId.REVERSE,
                    support = CapabilitySupport.UNKNOWN,
                    access = CapabilityAccess.READ_WRITE,
                    category = FeatureCategory.ADVANCED,
                    verification = VerificationState.UNKNOWN,
                    risk = CapabilityRisk.MEDIUM,
                    persistence = PersistenceBehavior.UNKNOWN,
                    requiresIdle = true,
                ),
            )),
        )
        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun readPosition() = 0
        override suspend fun readMovementState() = MovementState.IDLE
        override suspend fun moveAbsolute(position: Int) = Unit
        override suspend fun stop() = Unit
        override suspend fun readCapability(id: CapabilityId): CapabilityValue {
            readCalls++
            return CapabilityValue.BooleanValue(false)
        }
        override suspend fun writeCapability(id: CapabilityId, value: CapabilityValue) = Unit
    }
}
