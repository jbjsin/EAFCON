package dev.sphc.eafcon.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDeviceDisplayInfoTest {
    @Test
    fun parsesUsbBusAndDeviceFromCurrentDevicePath() {
        assertEquals("001" to "005", UsbDeviceDisplayInfo.parseBusAndDevice("/dev/bus/usb/001/005"))
        assertEquals(null, UsbDeviceDisplayInfo.parseBusAndDevice("unexpected-name"))
    }

    @Test
    fun compactLabelDistinguishesSameVidPidByCurrentLocation() {
        val first = info("/dev/bus/usb/001/005", "001", "005")
        val second = info("/dev/bus/usb/001/007", "001", "007")

        assertTrue(first.compactLabel.endsWith("001/005"))
        assertTrue(second.compactLabel.endsWith("001/007"))
        assertTrue(first.compactLabel != second.compactLabel)
        assertTrue(first.detailLines().any { it == "Serial: unavailable" })
    }

    private fun info(path: String, bus: String, device: String) = UsbDeviceDisplayInfo(
        deviceName = path,
        deviceId = 8,
        vendorId = 0x1A86,
        productId = 0x7523,
        manufacturer = null,
        product = "USB Serial",
        serialNumber = null,
        driverName = "CH34x",
        busNumber = bus,
        deviceNumber = device,
        deviceClass = 0,
        deviceSubclass = 0,
        deviceProtocol = 0,
        interfaceCount = 1,
    )
}
