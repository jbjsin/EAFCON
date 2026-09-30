package dev.sphc.eafcon.control

import dev.sphc.eafcon.protocol.GeminiProtocol
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
        controller.connect(FakeSerialTransport())

        val state = controller.state.value
        assertTrue(state.connected)
        assertEquals(7500, state.currentPosition)
        assertEquals(15000, state.deviceMaximum)
        assertEquals(MovementState.IDLE, state.movement)
    }

    @Test fun handshakeFailureLeavesControllerDisconnected() = controllerTest { controller ->
        val result = runCatching { controller.connect(FakeSerialTransport(handshakeResponse = "NO#")) }

        assertTrue(result.isFailure)
        assertFalse(controller.state.value.connected)
        assertNotNull(controller.state.value.lastError)
    }

    @Test fun absoluteMoveCompletesLifecycleAndRefreshesFinalPosition() = controllerTest { controller ->
        val transport = FakeSerialTransport(movementPollsPerMove = 4)
        controller.connect(transport)
        controller.setSoftwareMaximum(12000)

        controller.moveTo(9000)
        assertTrue(controller.state.value.commandPending)
        awaitState(controller) { it.movement == MovementState.MOVING }
        awaitState(controller) { it.movement == MovementState.IDLE && !it.commandPending && it.currentPosition == 9000 }

        assertTrue(transport.sentCommands.contains(":059000#"))
    }

    @Test fun relativeMoveUsesKnownPositionAndConfiguredMaximum() = controllerTest { controller ->
        val transport = FakeSerialTransport(movementPollsPerMove = 2)
        controller.connect(transport)
        controller.setSoftwareMaximum(12000)

        controller.moveBy(25)
        awaitState(controller) { it.movement == MovementState.IDLE && !it.commandPending && it.currentPosition == 7525 }

        assertTrue(transport.sentCommands.contains(":057525#"))
    }

    @Test fun moveIsRejectedWhileMoving() = controllerTest { controller ->
        controller.connect(FakeSerialTransport(movementPollsPerMove = 20))
        controller.setSoftwareMaximum(12000)
        controller.moveTo(9000)
        awaitState(controller) { it.movement == MovementState.MOVING }

        assertTrue(runCatching { controller.moveTo(8000) }.isFailure)
    }

    @Test fun moveRequiresSoftwareMaximumAndHonorsDeviceMaximum() = controllerTest { controller ->
        controller.connect(FakeSerialTransport(maximum = 10000))

        assertTrue(runCatching { controller.moveTo(8000) }.isFailure)
        assertTrue(runCatching { controller.setSoftwareMaximum(10001) }.isFailure)
    }

    @Test fun disconnectResetsTrustedState() = controllerTest { controller ->
        controller.connect(FakeSerialTransport())
        controller.setSoftwareMaximum(12000)

        controller.disconnect()

        assertEquals(FocuserState(), controller.state.value)
    }

    @Test fun transportFailureDisconnectsAndReportsError() = controllerTest { controller ->
        val failingCommand = GeminiProtocol.moveAbsolute(7600)
        controller.connect(FakeSerialTransport(failOnSendCommand = failingCommand))
        controller.setSoftwareMaximum(12000)

        assertTrue(runCatching { controller.moveTo(7600) }.isFailure)
        assertFalse(controller.state.value.connected)
        assertNotNull(controller.state.value.lastError)
    }

    @Test fun stopDuringMotionSendsAbortAndReturnsToIdle() = controllerTest { controller ->
        val transport = FakeSerialTransport(movementPollsPerMove = 20)
        controller.connect(transport)
        controller.setSoftwareMaximum(12000)
        controller.moveTo(9000)
        awaitState(controller) { it.movement == MovementState.MOVING }

        controller.stop()
        awaitState(controller) { it.movement == MovementState.IDLE && !it.commandPending }

        assertTrue(transport.sentCommands.contains(GeminiProtocol.STOP))
        assertEquals(7500, controller.state.value.currentPosition)
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
