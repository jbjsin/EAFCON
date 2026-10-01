package dev.sphc.eafcon.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
