package com.astrophoto.geminifocuser.usb

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
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String?,
    val product: String?,
) {
    val stableLabel: String get() = buildString {
        append(name)
        append(" · VID %04X PID %04X".format(vendorId, productId))
        manufacturer?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
        product?.takeIf(String::isNotBlank)?.let { append(" / ").append(it) }
    }
}

class UsbSerialDeviceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    fun listDevices(): List<UsbSerialDevice> = usbManager.deviceList.values
        .filter { UsbSerialProber.getDefaultProber().probeDevice(it) != null }
        .map { device ->
            UsbSerialDevice(
                usbDevice = device,
                name = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                manufacturer = device.manufacturerName,
                product = device.productName,
            )
        }
        .sortedBy { it.name }

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
}
