package dev.sphc.eafcon.usb

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbDeviceScanTest {
    @Test fun oneStaleDeviceDoesNotDiscardSuccessfullyInspectedNeighbors() {
        val devices = listOf("relay", "detached-focuser", "camera")

        val inspected = mapDevicesIndependently(devices) { device ->
            if (device == "detached-focuser") error("Device disappeared during enumeration")
            device.uppercase()
        }

        assertEquals(listOf("RELAY", "CAMERA"), inspected)
    }

    @Test fun unsupportedDevicesAreSkippedWithoutAffectingSupportedDevices() {
        val inspected = mapDevicesIndependently(listOf(1, 2, 3)) { value ->
            value.takeIf { it % 2 == 1 }
        }

        assertEquals(listOf(1, 3), inspected)
    }
}
