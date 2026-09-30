package dev.sphc.eafcon.usb

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbDeviceSelectionTest {
    @Test fun noDeviceLeavesSelectionEmpty() {
        assertEquals(UsbDeviceSelection(null, false), selectUsbDevice(null, false, emptyList()))
    }

    @Test fun oneDeviceMayBeSelectedAutomatically() {
        assertEquals(
            UsbDeviceSelection("/dev/bus/usb/001/005", false),
            selectUsbDevice(null, false, listOf("/dev/bus/usb/001/005")),
        )
    }

    @Test fun multipleDevicesRequireExplicitSelection() {
        assertEquals(
            UsbDeviceSelection(null, false),
            selectUsbDevice(null, false, listOf("device-a", "device-b")),
        )
    }

    @Test fun existingSelectionSurvivesRescanWhenStillPresent() {
        assertEquals(
            UsbDeviceSelection("device-b", true),
            selectUsbDevice("device-b", true, listOf("device-a", "device-b")),
        )
    }

    @Test fun missingSelectionIsNotReplacedWhenMultipleDevicesRemain() {
        assertEquals(
            UsbDeviceSelection(null, false),
            selectUsbDevice("removed", true, listOf("device-a", "device-b")),
        )
    }

    @Test fun automaticSelectionIsClearedWhenASecondDeviceAppears() {
        assertEquals(
            UsbDeviceSelection(null, false),
            selectUsbDevice("device-a", false, listOf("device-a", "device-b")),
        )
    }
}
