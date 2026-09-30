package dev.sphc.eafcon.usb

data class UsbDeviceDisplayInfo(
    val deviceName: String,
    val deviceId: Int,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String?,
    val product: String?,
    val serialNumber: String?,
    val driverName: String?,
    val busNumber: String?,
    val deviceNumber: String?,
    val deviceClass: Int?,
    val deviceSubclass: Int?,
    val deviceProtocol: Int?,
    val interfaceCount: Int,
) {
    val compactLocation: String get() = if (busNumber != null && deviceNumber != null) {
        "$busNumber/$deviceNumber"
    } else {
        deviceName.substringAfterLast('/').ifBlank { "Device $deviceId" }
    }

    val compactLabel: String get() = buildString {
        append("USB Serial")
        driverName?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
        append(" · %04X:%04X".format(vendorId, productId))
        append(" · ").append(compactLocation)
    }

    fun detailLines(): List<String> = buildList {
        add("Driver: ${driverName ?: "unavailable"}")
        add("VID: %04X".format(vendorId))
        add("PID: %04X".format(productId))
        add("Bus: ${busNumber ?: "unavailable"}")
        add("Device: ${deviceNumber ?: "unavailable"}")
        add("Device path: $deviceName")
        add("Device ID (current session): $deviceId")
        add("Manufacturer: ${manufacturer?.takeIf(String::isNotBlank) ?: "unavailable"}")
        add("Product: ${product?.takeIf(String::isNotBlank) ?: "unavailable"}")
        add("Serial: ${serialNumber?.takeIf(String::isNotBlank) ?: "unavailable"}")
        add("Class: ${deviceClass?.toString() ?: "unavailable"}")
        add("Subclass: ${deviceSubclass?.toString() ?: "unavailable"}")
        add("Protocol: ${deviceProtocol?.toString() ?: "unavailable"}")
        add("Interfaces: $interfaceCount")
    }

    companion object {
        fun parseBusAndDevice(deviceName: String): Pair<String, String>? {
            val match = USB_PATH.matchEntire(deviceName) ?: return null
            return match.groupValues[1] to match.groupValues[2]
        }

        private val USB_PATH = Regex(".*/usb/(\\d+)/(\\d+)")
    }
}
