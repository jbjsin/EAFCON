package com.astrophoto.geminifocuser.control

import com.astrophoto.geminifocuser.usb.FakeSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class FocuserControllerTest {
    @Test fun stopDuringMotionReturnsToIdleAndKeepsCurrentPosition() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = FocuserController(scope)
        try {
            controller.connect(FakeSerialTransport())
            controller.setSoftwareMaximum(12000)
            controller.moveTo(9000)

            withTimeout(2_000) {
                while (controller.state.value.movement != MovementState.MOVING) delay(10)
            }
            controller.stop()
            withTimeout(2_000) {
                while (controller.state.value.movement != MovementState.IDLE) delay(10)
            }

            assertEquals(7500, controller.state.value.currentPosition)
            assertEquals(false, controller.state.value.commandPending)
        } finally {
            controller.disconnect()
            scope.cancel()
        }
    }
}
