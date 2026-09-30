package dev.sphc.eafcon.usb

data class UsbDeviceSelection(
    val deviceName: String?,
    val isExplicit: Boolean,
)

/**
 * Keeps an explicit current-session selection when it still exists. A single compatible device
 * may be selected automatically; multiple devices always require an explicit choice.
 */
fun selectUsbDevice(
    previousSelection: String?,
    previousSelectionWasExplicit: Boolean,
    availableDeviceNames: List<String>,
): UsbDeviceSelection {
    if (previousSelectionWasExplicit && previousSelection in availableDeviceNames) {
        return UsbDeviceSelection(previousSelection, isExplicit = true)
    }
    return UsbDeviceSelection(availableDeviceNames.singleOrNull(), isExplicit = false)
}
