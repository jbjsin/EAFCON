package com.astrophoto.geminifocuser.usb

import android.hardware.usb.UsbDeviceConnection
import com.astrophoto.geminifocuser.protocol.DelimitedResponseParser
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class UsbSerialPortTransport(
    private val connection: UsbDeviceConnection,
    private val port: UsbSerialPort,
) : SerialTransport {
    private val mutex = Mutex()
    private val parser = DelimitedResponseParser()
    @Volatile private var opened = false

    override suspend fun open() = withContext(Dispatchers.IO) {
        check(!opened) { "Serial port is already open" }
        port.open(connection)
        port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port.setFlowControl(UsbSerialPort.FlowControl.NONE)
        opened = true
    }

    override suspend fun send(command: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(opened) { "Serial port is closed" }
            port.write(command.toByteArray(StandardCharsets.US_ASCII), IO_TIMEOUT_MS)
        }
    }

    override suspend fun exchange(command: String, expectedPrefix: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(opened) { "Serial port is closed" }
            port.write(command.toByteArray(StandardCharsets.US_ASCII), IO_TIMEOUT_MS)
            val deadline = System.nanoTime() + RESPONSE_TIMEOUT_NS
            val buffer = ByteArray(256)
            while (System.nanoTime() < deadline) {
                val count = port.read(buffer, READ_TIMEOUT_MS)
                if (count <= 0) continue
                val chunk = String(buffer, 0, count, StandardCharsets.US_ASCII)
                val frames = parser.append(chunk)
                frames.firstOrNull { it.startsWith(expectedPrefix) }?.let { return@withContext it }
            }
            throw java.io.IOException("Timed out waiting for a Gemini response")
        }
    }

    override suspend fun close() = mutex.withLock {
        withContext(Dispatchers.IO) {
            opened = false
            parser.reset()
            runCatching { port.close() }
            connection.close()
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 250
        const val IO_TIMEOUT_MS = 1000
        const val RESPONSE_TIMEOUT_NS = 3_000_000_000L
    }
}
