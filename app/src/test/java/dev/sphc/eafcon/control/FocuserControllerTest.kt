package dev.sphc.eafcon.control

import dev.sphc.eafcon.protocol.GeminiProtocol
import dev.sphc.eafcon.driver.MyFocuserPro2Driver
import dev.sphc.eafcon.driver.CapabilityId
import dev.sphc.eafcon.driver.CapabilityValue
import dev.sphc.eafcon.driver.FeatureCategory
import dev.sphc.eafcon.usb.FakeSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocuserControllerTest {
    @Test fun successfulConnectPopulatesInitialState() = controllerTest { controller ->
        controller.connect(MyFocuserPro2Driver { FakeSerialTransport() })

        val state = controller.state.value
        assertTrue(state.connected)
        assertEquals(7500, state.currentPosition)
        assertEquals(15000, state.deviceMaximum)
        assertEquals(MovementState.IDLE, state.movement)
    }

    @Test fun handshakeFailureLeavesControllerDisconnected() = controllerTest { controller ->
        val result = runCatching { controller.connect(MyFocuserPro2Driver { FakeSerialTransport(handshakeResponse = "NO#") }) }

        assertTrue(result.isFailure)
        assertFalse(controller.state.value.connected)
        assertNotNull(controller.state.value.lastError)
    }

    @Test fun absoluteMoveCompletesLifecycleAndRefreshesFinalPosition() = controllerTest { controller ->
        val transport = FakeSerialTransport(movementPollsPerMove = 4)
        controller.connect(MyFocuserPro2Driver { transport })
        controller.setSoftwareMaximum(12000)

        controller.moveTo(9000)
        assertTrue(controller.state.value.commandPending)
        awaitState(controller) { it.movement == MovementState.MOVING }
        awaitState(controller) { it.movement == MovementState.IDLE && !it.commandPending && it.currentPosition == 9000 }

        assertTrue(transport.sentCommands.contains(":059000#"))
    }

    @Test fun relativeMoveUsesKnownPositionAndConfiguredMaximum() = controllerTest { controller ->
        val transport = FakeSerialTransport(movementPollsPerMove = 2)
        controller.connect(MyFocuserPro2Driver { transport })
        controller.setSoftwareMaximum(12000)

        controller.moveBy(25)
        awaitState(controller) { it.movement == MovementState.IDLE && !it.commandPending && it.currentPosition == 7525 }

        assertTrue(transport.sentCommands.contains(":057525#"))
    }

    @Test fun moveIsRejectedWhileMoving() = controllerTest { controller ->
        controller.connect(MyFocuserPro2Driver { FakeSerialTransport(movementPollsPerMove = 20) })
        controller.setSoftwareMaximum(12000)
        controller.moveTo(9000)
        awaitState(controller) { it.movement == MovementState.MOVING }

        assertTrue(runCatching { controller.moveTo(8000) }.isFailure)
    }

    @Test fun moveRequiresSoftwareMaximumAndHonorsDeviceMaximum() = controllerTest { controller ->
        controller.connect(MyFocuserPro2Driver { FakeSerialTransport(maximum = 10000) })

        assertTrue(runCatching { controller.moveTo(8000) }.isFailure)
        assertTrue(runCatching { controller.setSoftwareMaximum(10001) }.isFailure)
    }

    @Test fun disconnectResetsTrustedState() = controllerTest { controller ->
        controller.connect(MyFocuserPro2Driver { FakeSerialTransport() })
        controller.setSoftwareMaximum(12000)

        controller.disconnect()

        assertEquals(FocuserState(), controller.state.value)
    }

    @Test fun transportFailureDisconnectsAndReportsError() = controllerTest { controller ->
        val failingCommand = GeminiProtocol.moveAbsolute(7600)
        controller.connect(MyFocuserPro2Driver { FakeSerialTransport(failOnSendCommand = failingCommand) })
        controller.setSoftwareMaximum(12000)

        assertTrue(runCatching { controller.moveTo(7600) }.isFailure)
        assertFalse(controller.state.value.connected)
        assertNotNull(controller.state.value.lastError)
    }

    @Test fun stopDuringMotionSendsAbortAndReturnsToIdle() = controllerTest { controller ->
        val transport = FakeSerialTransport(movementPollsPerMove = 20)
        controller.connect(MyFocuserPro2Driver { transport })
        controller.setSoftwareMaximum(12000)
        controller.moveTo(9000)
        awaitState(controller) { it.movement == MovementState.MOVING }

        controller.stop()
        awaitState(controller) { it.movement == MovementState.IDLE && !it.commandPending }

        assertTrue(transport.sentCommands.contains(GeminiProtocol.STOP))
        assertEquals(7500, controller.state.value.currentPosition)
    }

    @Test fun advancedSettingsReadWriteWithReadbackAndRejectAdminOperations() = controllerTest { controller ->
        val transport = FakeSerialTransport()
        controller.connect(MyFocuserPro2Driver { transport })

        assertEquals(CapabilityValue.BooleanValue(false), controller.readCapability(CapabilityId.REVERSE))
        assertEquals(
            CapabilityValue.BooleanValue(true),
            controller.writeCapability(CapabilityId.REVERSE, CapabilityValue.BooleanValue(true)),
        )
        assertTrue(transport.sentCommands.contains(":141#"))
        assertEquals(CapabilityValue.IntegerValue(1), controller.readCapability(CapabilityId.MOTOR_SPEED))
        assertTrue(
            runCatching { controller.writeCapability(CapabilityId.MOTOR_SPEED, CapabilityValue.IntegerValue(3)) }.isFailure,
        )
        assertTrue(runCatching { controller.readCapability(CapabilityId.STEP_MODE) }.isFailure)
        assertFalse(transport.sentCommands.contains(":3032#"))
    }

    @Test fun advancedWritesAreBlockedWhileMovingBeforeTransportWrite() = controllerTest { controller ->
        val transport = FakeSerialTransport(movementPollsPerMove = 20)
        controller.connect(MyFocuserPro2Driver { transport })
        controller.setSoftwareMaximum(12000)
        controller.moveTo(9000)
        awaitState(controller) { it.movement == MovementState.MOVING }

        assertTrue(
            runCatching { controller.writeCapability(CapabilityId.REVERSE, CapabilityValue.BooleanValue(true)) }.isFailure,
        )
        assertFalse(transport.sentCommands.contains(":141#"))
    }

    @Test fun administrativeStepModeInvalidatesPositionUntilExplicitSync() = controllerTest { controller ->
        val transport = FakeSerialTransport()
        controller.connect(MyFocuserPro2Driver { transport })
        controller.setSoftwareMaximum(12000)

        assertEquals(CapabilityValue.IntegerValue(1), controller.readCapability(CapabilityId.STEP_MODE, FeatureCategory.ADMINISTRATIVE))
        assertEquals(
            CapabilityValue.IntegerValue(2),
            controller.writeCapability(CapabilityId.STEP_MODE, CapabilityValue.IntegerValue(2), FeatureCategory.ADMINISTRATIVE),
        )
        assertTrue(controller.state.value.positionNeedsSync)
        assertEquals(null, controller.state.value.currentPosition)
        assertEquals(null, controller.state.value.softwareMaximum)
        assertTrue(runCatching { controller.moveTo(8000) }.isFailure)

        assertEquals(
            CapabilityValue.IntegerValue(7500),
            controller.writeCapability(CapabilityId.SYNC_POSITION, CapabilityValue.IntegerValue(7500), FeatureCategory.ADMINISTRATIVE),
        )
        assertFalse(controller.state.value.positionNeedsSync)
        assertEquals(7500, controller.state.value.currentPosition)
        assertTrue(runCatching { controller.moveTo(8000) }.isFailure)
        controller.setSoftwareMaximum(12000)
        assertTrue(
            runCatching {
                controller.writeCapability(CapabilityId.SET_MAX_POSITION, CapabilityValue.IntegerValue(13000), FeatureCategory.ADMINISTRATIVE)
            }.isFailure,
        )
        assertEquals(15000, controller.state.value.deviceMaximum)
        assertTrue(transport.sentCommands.contains(":302#"))
        assertTrue(transport.sentCommands.contains(":317500#"))
        assertFalse(transport.sentCommands.contains(":07013000#"))
        assertFalse(transport.sentCommands.contains(":058000#"))
    }

    @Test fun administrativeOneShotAndHardwareSettingsRequireSourceCapability() = controllerTest { controller ->
        val transport = FakeSerialTransport()
        controller.connect(MyFocuserPro2Driver { transport })
        controller.setSoftwareMaximum(12000)

        assertEquals(CapabilityValue.BooleanValue(true), controller.readCapability(CapabilityId.COIL_POWER, FeatureCategory.ADMINISTRATIVE))
        controller.writeCapability(CapabilityId.COIL_POWER, CapabilityValue.BooleanValue(false), FeatureCategory.ADMINISTRATIVE)
        controller.writeCapability(CapabilityId.DISPLAY_CONFIGURATION, CapabilityValue.BooleanValue(false), FeatureCategory.ADMINISTRATIVE)
        controller.writeCapability(CapabilityId.TEMPERATURE_UNIT, CapabilityValue.ChoiceValue("CELSIUS"), FeatureCategory.ADMINISTRATIVE)
        controller.writeCapability(CapabilityId.HOME, CapabilityValue.TriggerValue, FeatureCategory.ADMINISTRATIVE)

        assertTrue(transport.sentCommands.contains(":120#"))
        assertTrue(transport.sentCommands.contains(":360#"))
        assertTrue(transport.sentCommands.contains(":16#"))
        assertTrue(transport.sentCommands.contains(":28#"))
        assertTrue(
            runCatching {
                controller.writeCapability(CapabilityId.PERSIST_SETTINGS, CapabilityValue.TriggerValue, FeatureCategory.DEVICE_ADMINISTRATION)
            }.isFailure,
        )
        assertFalse(transport.sentCommands.any { it !in setOf(":120#", ":360#", ":16#", ":28#") })
    }

    @Test fun developmentBuildCanTestSourceBackedDeviceMaximumButDefaultDriverCannot() = controllerTest { controller ->
        val productionLikeTransport = FakeSerialTransport()
        val productionLike = MyFocuserPro2Driver { productionLikeTransport }
        assertTrue(productionLike.descriptor.capabilities[CapabilityId.SET_MAX_POSITION]?.unavailableReason != null)
        controller.connect(productionLike)
        controller.setSoftwareMaximum(12000)
        assertTrue(runCatching {
            controller.writeCapability(CapabilityId.SET_MAX_POSITION, CapabilityValue.IntegerValue(14000), FeatureCategory.ADMINISTRATIVE)
        }.isFailure)
        assertFalse(productionLikeTransport.sentCommands.any { it.startsWith(":07") })
        controller.disconnect()

        val developmentTransport = FakeSerialTransport()
        val developmentDriver = MyFocuserPro2Driver(enableUnverifiedDeviceMaximumWrite = true) { developmentTransport }
        assertEquals(null, developmentDriver.descriptor.capabilities[CapabilityId.SET_MAX_POSITION]?.unavailableReason)
        controller.connect(developmentDriver)
        controller.setSoftwareMaximum(12000)
        assertEquals(
            CapabilityValue.IntegerValue(14000),
            controller.writeCapability(CapabilityId.SET_MAX_POSITION, CapabilityValue.IntegerValue(14000), FeatureCategory.ADMINISTRATIVE),
        )
        assertEquals(14000, controller.state.value.deviceMaximum)
        assertTrue(developmentTransport.sentCommands.contains(":07014000#"))
    }

    @Test fun administrativeActionsAreBlockedWhileMoving() = controllerTest { controller ->
        val transport = FakeSerialTransport(movementPollsPerMove = 20)
        controller.connect(MyFocuserPro2Driver { transport })
        controller.setSoftwareMaximum(12000)
        controller.moveTo(9000)
        awaitState(controller) { it.movement == MovementState.MOVING }

        assertTrue(
            runCatching {
                controller.writeCapability(CapabilityId.COIL_POWER, CapabilityValue.BooleanValue(false), FeatureCategory.ADMINISTRATIVE)
            }.isFailure,
        )
        assertFalse(transport.sentCommands.contains(":120#"))
    }

    @Test fun stepModePositionInvalidationSurvivesDisconnectAndControllerRecreation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val requirementStore = InMemoryPositionSyncRequirementStore()
        val first = FocuserController(scope, pollIdleMs = 10, pollMovingMs = 5, pollTemperatureMs = 50, positionSyncStore = requirementStore)
        val second = FocuserController(scope, pollIdleMs = 10, pollMovingMs = 5, pollTemperatureMs = 50, positionSyncStore = requirementStore)
        try {
            first.connect(MyFocuserPro2Driver { FakeSerialTransport() })
            first.setSoftwareMaximum(12000)
            first.writeCapability(CapabilityId.STEP_MODE, CapabilityValue.IntegerValue(2), FeatureCategory.ADMINISTRATIVE)
            first.disconnect()

            second.connect(MyFocuserPro2Driver { FakeSerialTransport() })
            assertTrue(second.state.value.positionNeedsSync)
            assertEquals(null, second.state.value.currentPosition)
            assertTrue(runCatching { second.setSoftwareMaximum(12000) }.isFailure)
            second.writeCapability(CapabilityId.SYNC_POSITION, CapabilityValue.IntegerValue(7500), FeatureCategory.ADMINISTRATIVE)
            assertFalse(requirementStore.isRequired())
        } finally {
            first.disconnect()
            second.disconnect()
            scope.cancel()
        }
    }

    private fun controllerTest(block: suspend (FocuserController) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = FocuserController(scope, pollIdleMs = 10, pollMovingMs = 5, pollTemperatureMs = 50)
        try {
            block(controller)
        } finally {
            controller.disconnect()
            scope.cancel()
        }
    }

    private suspend fun awaitState(controller: FocuserController, predicate: (FocuserState) -> Boolean) {
        withTimeout(2_000) {
            while (!predicate(controller.state.value)) delay(5)
        }
    }
}
