package dev.sphc.eafcon.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.sphc.eafcon.driver.CapabilityId

class AppNavigationTest {
    @Test
    fun `settings returns to connection when entered from connection`() {
        assertEquals(
            AppPage.CONNECTION,
            secondaryBackDestination(AppPage.SETTINGS, AppPage.CONNECTION),
        )
    }

    @Test
    fun `settings returns to control when entered from control`() {
        assertEquals(
            AppPage.CONTROL,
            secondaryBackDestination(AppPage.SETTINGS, AppPage.CONTROL),
        )
    }

    @Test
    fun `primary pages leave Android Back to the system`() {
        assertNull(secondaryBackDestination(AppPage.CONNECTION, AppPage.CONTROL))
        assertNull(secondaryBackDestination(AppPage.CONTROL, AppPage.CONNECTION))
    }

    @Test
    fun `invalid secondary history falls back to connection`() {
        assertEquals(
            AppPage.CONNECTION,
            secondaryBackDestination(AppPage.SETTINGS, AppPage.SETTINGS),
        )
    }

    @Test
    fun `temperature direction zero means position increases as temperature rises`() {
        assertTrue(increaseOnTemperatureRiseFromProtocol(direction = false))
        assertFalse(increaseOnTemperatureRiseFromProtocol(direction = true))
        assertFalse(protocolDirectionForIncreaseOnTemperatureRise(increase = true))
        assertTrue(protocolDirectionForIncreaseOnTemperatureRise(increase = false))
    }

    @Test
    fun `controller restart is not exposed as a duplicate device administration action`() {
        assertFalse(showDeviceAdministrationAction(CapabilityId.RESET_CONTROLLER))
        assertTrue(showDeviceAdministrationAction(CapabilityId.PERSIST_SETTINGS))
        assertTrue(showDeviceAdministrationAction(CapabilityId.RESTORE_DEFAULTS))
    }
}
