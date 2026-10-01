package dev.sphc.eafcon.usb

import android.hardware.usb.UsbDeviceConnection
import dev.sphc.eafcon.protocol.DelimitedResponseParser
import dev.sphc.eafcon.settings.ConnectionProfileRules
import dev.sphc.eafcon.settings.SerialFlowControl
import dev.sphc.eafcon.settings.SerialParameters
import dev.sphc.eafcon.settings.SerialParity
import dev.sphc.eafcon.settings.SerialStopBits
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class UsbSerialPortTransport(
    private val connection: UsbDeviceConnection,
    private val port: UsbSerialPort,
    serialParameters: SerialParameters,
) : SerialTransport {
    private val parameters = ConnectionProfileRules.validate(serialParameters)
    private val mutex = Mutex()
    private val parser = DelimitedResponseParser()
    @Volatile private var opened = false

    override suspend fun open() = withContext(Dispatchers.IO) {
        check(!opened) { "Serial port is already open" }
        port.open(connection)
        port.setParameters(
            parameters.baudRate,
            parameters.dataBits,
            parameters.stopBits.toUsbValue(),
            parameters.parity.toUsbValue(),
        )
        port.setFlowControl(parameters.flowControl.toUsbValue())
        parser.reset()
        opened = true
    }

    override suspend fun send(command: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(opened) { "Serial port is closed" }
            port.write(command.toByteArray(StandardCharsets.US_ASCII), parameters.writeTimeoutMs)
        }
    }

    override suspend fun exchange(command: String, expectedPrefix: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(opened) { "Serial port is closed" }
            // Discard a partial frame left behind by a previous timed-out exchange.
            parser.reset()
            port.write(command.toByteArray(StandardCharsets.US_ASCII), parameters.writeTimeoutMs)
            val deadline = System.nanoTime() + parameters.responseTimeoutMs * NANOS_PER_MILLISECOND
            val buffer = ByteArray(256)
            while (System.nanoTime() < deadline) {
                val count = port.read(buffer, parameters.readTimeoutMs)
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
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

private fun SerialStopBits.toUsbValue(): Int = when (this) {
    SerialStopBits.ONE -> UsbSerialPort.STOPBITS_1
    SerialStopBits.ONE_POINT_FIVE -> UsbSerialPort.STOPBITS_1_5
    SerialStopBits.TWO -> UsbSerialPort.STOPBITS_2
}

private fun SerialParity.toUsbValue(): Int = when (this) {
    SerialParity.NONE -> UsbSerialPort.PARITY_NONE
    SerialParity.ODD -> UsbSerialPort.PARITY_ODD
    SerialParity.EVEN -> UsbSerialPort.PARITY_EVEN
    SerialParity.MARK -> UsbSerialPort.PARITY_MARK
    SerialParity.SPACE -> UsbSerialPort.PARITY_SPACE
}

private fun SerialFlowControl.toUsbValue(): UsbSerialPort.FlowControl = when (this) {
    SerialFlowControl.NONE -> UsbSerialPort.FlowControl.NONE
    SerialFlowControl.RTS_CTS -> UsbSerialPort.FlowControl.RTS_CTS
    SerialFlowControl.DTR_DSR -> UsbSerialPort.FlowControl.DTR_DSR
    SerialFlowControl.XON_XOFF -> UsbSerialPort.FlowControl.XON_XOFF
    SerialFlowControl.XON_XOFF_INLINE -> UsbSerialPort.FlowControl.XON_XOFF_INLINE
}
