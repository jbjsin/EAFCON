package com.astrophoto.geminifocuser.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astrophoto.geminifocuser.control.FocuserController
import com.astrophoto.geminifocuser.control.FocuserState
import com.astrophoto.geminifocuser.usb.UsbSerialDevice
import com.astrophoto.geminifocuser.usb.UsbSerialDeviceRepository
import com.astrophoto.geminifocuser.usb.FakeSerialTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FocuserUiState(
    val devices: List<UsbSerialDevice> = emptyList(),
    val selectedDeviceName: String? = null,
    val focuser: FocuserState = FocuserState(),
    val isWorking: Boolean = false,
    val demoMode: Boolean = false,
    val message: String? = null,
)

class FocuserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UsbSerialDeviceRepository(application)
    private val controller = FocuserController(viewModelScope)
    private val mutableState = MutableStateFlow(FocuserUiState())
    val state: StateFlow<FocuserUiState> = mutableState.asStateFlow()
    private var connectedDeviceId: Int? = null
    private val usbEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> refreshDevices()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.usbDeviceExtra()
                    refreshDevices()
                    if (device?.deviceId == connectedDeviceId) {
                        viewModelScope.launch {
                            controller.disconnect()
                            connectedDeviceId = null
                            mutableState.update {
                                it.copy(demoMode = false, message = "USB device detached; focuser disconnected")
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            application.registerReceiver(usbEventsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            application.registerReceiver(usbEventsReceiver, filter)
        }
        viewModelScope.launch {
            controller.state.collect { focuser ->
                if (!focuser.connected) connectedDeviceId = null
                mutableState.update {
                    it.copy(focuser = focuser, demoMode = if (!focuser.connected) false else it.demoMode)
                }
            }
        }
        refreshDevices()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            val devices = runCatching { withContext(Dispatchers.IO) { repository.listDevices() } }
                .getOrElse { error ->
                    mutableState.update { it.copy(message = error.message ?: "Unable to scan USB devices") }
                    emptyList()
                }
            mutableState.update { old ->
                val selected = old.selectedDeviceName?.takeIf { name -> devices.any { it.name == name } }
                    ?: devices.firstOrNull()?.name
                old.copy(devices = devices, selectedDeviceName = selected)
            }
        }
    }

    fun selectDevice(name: String) = mutableState.update { it.copy(selectedDeviceName = name, message = null) }

    fun connect() {
        viewModelScope.launch {
            val selected = mutableState.value.devices.firstOrNull { it.name == mutableState.value.selectedDeviceName }
            if (selected == null) {
                mutableState.update { it.copy(message = "Select a USB serial device first") }
                return@launch
            }
            mutableState.update { it.copy(isWorking = true, message = "Requesting USB permission…") }
            try {
                val permitted = repository.requestPermission(selected.usbDevice)
                check(permitted) { "USB permission was denied" }
                val transport = withContext(Dispatchers.IO) { repository.open(selected.usbDevice) }
                controller.connect(transport)
                connectedDeviceId = selected.usbDevice.deviceId
                mutableState.update { it.copy(demoMode = false, message = "Connected to ${selected.name}") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(message = error.message ?: "USB connection failed") }
            } finally {
                mutableState.update { it.copy(isWorking = false) }
                refreshDevices()
            }
        }
    }

    fun connectDemo() {
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, message = "Starting simulated focuser…") }
            try {
                controller.connect(FakeSerialTransport())
                connectedDeviceId = null
                mutableState.update { it.copy(demoMode = true, message = "Demo mode: simulated focuser at 7500 steps") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(message = error.message ?: "Demo connection failed") }
            } finally {
                mutableState.update { it.copy(isWorking = false) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            controller.disconnect()
            connectedDeviceId = null
            mutableState.update { it.copy(demoMode = false, message = "Disconnected") }
        }
    }

    fun setSoftwareMaximum(value: String) {
        val maximum = value.toIntOrNull()
        if (maximum == null) {
            mutableState.update { it.copy(message = "Enter a valid positive software maximum") }
            return
        }
        runCatching { controller.setSoftwareMaximum(maximum) }
            .onSuccess { mutableState.update { it.copy(message = "Software safety maximum set to $maximum") } }
            .onFailure { error -> mutableState.update { it.copy(message = error.message) } }
    }

    fun moveTo(value: String) = launchMovement {
        val target = value.toIntOrNull() ?: error("Enter a valid target position")
        controller.moveTo(target)
        "Move command sent to $target"
    }

    fun moveBy(delta: Int) = launchMovement {
        controller.moveBy(delta)
        "Relative move command sent (${if (delta >= 0) "+" else ""}$delta)"
    }

    fun stop() {
        viewModelScope.launch {
            runCatching { controller.stop() }
                .onSuccess { mutableState.update { it.copy(message = "Stop command sent") } }
                .onFailure { error -> mutableState.update { it.copy(message = error.message ?: "Stop failed") } }
        }
    }

    private fun launchMovement(action: suspend () -> String) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { message -> mutableState.update { it.copy(message = message) } }
                .onFailure { error -> mutableState.update { it.copy(message = error.message ?: "Move failed") } }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { getApplication<Application>().unregisterReceiver(usbEventsReceiver) }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            controller.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}
