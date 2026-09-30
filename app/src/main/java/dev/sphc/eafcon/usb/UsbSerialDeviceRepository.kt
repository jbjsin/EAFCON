package dev.sphc.eafcon.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class UsbSerialDevice(
    val usbDevice: UsbDevice,
    val displayInfo: UsbDeviceDisplayInfo,
) {
    val name: String get() = displayInfo.deviceName
    val stableLabel: String get() = displayInfo.compactLabel
}

class UsbSerialDeviceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    fun listDevices(): List<UsbSerialDevice> {
        val prober = UsbSerialProber.getDefaultProber()
        return usbManager.deviceList.values.mapNotNull { device ->
            // Probing maps descriptors to a serial driver; it does not open or claim the device.
            val driver = prober.probeDevice(device) ?: return@mapNotNull null
            val busAndDevice = UsbDeviceDisplayInfo.parseBusAndDevice(device.deviceName)
            val rawDriverName = driver.javaClass.simpleName.removeSuffix("SerialDriver")
            val displayDriverName = if (rawDriverName.equals("Ch34x", ignoreCase = true)) "CH34x" else rawDriverName
            val info = UsbDeviceDisplayInfo(
                deviceName = device.deviceName,
                deviceId = device.deviceId,
                vendorId = device.vendorId,
                productId = device.productId,
                manufacturer = readDescriptor { device.manufacturerName },
                product = readDescriptor { device.productName },
                // Android may require USB permission for this descriptor. Scanning never requests it.
                serialNumber = readDescriptor { device.serialNumber },
                driverName = displayDriverName.takeIf(String::isNotBlank),
                busNumber = busAndDevice?.first,
                deviceNumber = busAndDevice?.second,
                deviceClass = device.deviceClass,
                deviceSubclass = device.deviceSubclass,
                deviceProtocol = device.deviceProtocol,
                interfaceCount = device.interfaceCount,
            )
            UsbSerialDevice(device, info)
        }.sortedBy { it.name }
    }

    suspend fun requestPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true
        return suspendCancellableCoroutine { continuation ->
            val action = "${appContext.packageName}.USB_PERMISSION"
            val filter = IntentFilter(action)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != action) return
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (continuation.isActive) continuation.resume(granted)
                    runCatching { appContext.unregisterReceiver(this) }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            val request = Intent(action).setPackage(appContext.packageName)
            val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                device.deviceId,
                request,
                PendingIntent.FLAG_UPDATE_CURRENT or mutability,
            )
            continuation.invokeOnCancellation {
                runCatching { appContext.unregisterReceiver(receiver) }
                pendingIntent.cancel()
            }
            usbManager.requestPermission(device, pendingIntent)
        }
    }

    fun open(device: UsbDevice): SerialTransport {
        check(usbManager.hasPermission(device)) { "USB permission has not been granted" }
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: error("No supported USB serial driver was found")
        val port = driver.ports.firstOrNull() ?: error("USB serial device has no ports")
        val connection = usbManager.openDevice(device) ?: error("Android could not open the USB device")
        return UsbSerialPortTransport(connection, port)
    }

    private inline fun readDescriptor(read: () -> String?): String? =
        runCatching(read).getOrNull()?.takeIf(String::isNotBlank)
}
