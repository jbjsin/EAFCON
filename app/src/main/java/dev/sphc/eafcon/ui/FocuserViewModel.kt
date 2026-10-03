package dev.sphc.eafcon.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sphc.eafcon.BuildConfig
import dev.sphc.eafcon.control.FocuserController
import dev.sphc.eafcon.control.FocuserState
import dev.sphc.eafcon.control.PositionSyncRequirementStore
import dev.sphc.eafcon.driver.CapabilityId
import dev.sphc.eafcon.driver.CapabilityValue
import dev.sphc.eafcon.driver.FeatureCategory
import dev.sphc.eafcon.driver.CapabilityAccess
import dev.sphc.eafcon.driver.FocuserType
import dev.sphc.eafcon.driver.MyFocuserPro2Driver
import dev.sphc.eafcon.settings.TemperatureDisplayUnit
import dev.sphc.eafcon.presets.PositionPreset
import dev.sphc.eafcon.presets.PositionPresetStore
import dev.sphc.eafcon.presets.PresetFileException
import dev.sphc.eafcon.presets.PresetJsonCodec
import dev.sphc.eafcon.presets.PresetRules
import dev.sphc.eafcon.settings.ConnectionProfile
import dev.sphc.eafcon.settings.ConnectionProfileCatalog
import dev.sphc.eafcon.settings.ConnectionProfileRules
import dev.sphc.eafcon.settings.ConnectionProfileStore
import dev.sphc.eafcon.settings.FocuserTypeStore
import dev.sphc.eafcon.settings.SerialParameters
import dev.sphc.eafcon.usb.UsbSerialDevice
import dev.sphc.eafcon.usb.UsbSerialDeviceRepository
import dev.sphc.eafcon.usb.FakeSerialTransport
import dev.sphc.eafcon.usb.selectUsbDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

data class FocuserUiState(
    val devices: List<UsbSerialDevice> = emptyList(),
    val selectedDeviceName: String? = null,
    val selectedDeviceIsExplicit: Boolean = false,
    val focuser: FocuserState = FocuserState(),
    val isWorking: Boolean = false,
    val demoMode: Boolean = false,
    val message: String? = null,
    val presets: List<PositionPreset> = emptyList(),
    val capabilities: dev.sphc.eafcon.driver.CapabilitySet? = null,
    val advancedValues: Map<CapabilityId, CapabilityValue> = emptyMap(),
    val advancedErrors: Map<CapabilityId, String> = emptyMap(),
    val advancedLoading: Boolean = false,
    val temperatureDisplayUnit: TemperatureDisplayUnit = TemperatureDisplayUnit.CELSIUS,
    val focuserType: FocuserType = FocuserType.GEMINI_FOCUSER_PRO,
    val connectionProfile: ConnectionProfile,
)

class FocuserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UsbSerialDeviceRepository(application)
    private val presetStore = PositionPresetStore(application)
    private val connectionProfileStore = ConnectionProfileStore(application)
    private val focuserTypeStore = FocuserTypeStore(application)
    private val displayPreferences = application.getSharedPreferences("eafcon_display_settings", Context.MODE_PRIVATE)
    private var connectionProfileCatalog: ConnectionProfileCatalog = connectionProfileStore.load()
    private val adminStatePreferences = application.getSharedPreferences("eafcon_admin_state", Context.MODE_PRIVATE)
    private val positionSyncStore = object : PositionSyncRequirementStore {
        override fun isRequired(): Boolean = adminStatePreferences.getBoolean(KEY_POSITION_SYNC_REQUIRED, false)
        override fun setRequired(required: Boolean) {
            check(adminStatePreferences.edit().putBoolean(KEY_POSITION_SYNC_REQUIRED, required).commit()) {
                "Unable to persist the position synchronization safety state"
            }
        }
    }
    // Keep polling bound to ViewModel lifetime while allowing onCleared() to join it without
    // blocking the Main dispatcher that normally backs viewModelScope.
    private val controller = FocuserController(
        CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO),
        positionSyncStore = positionSyncStore,
    )
    private val mutableState = MutableStateFlow(
        FocuserUiState(
            presets = runCatching { presetStore.load() }.getOrDefault(emptyList()),
            temperatureDisplayUnit = runCatching {
                TemperatureDisplayUnit.valueOf(displayPreferences.getString(KEY_TEMPERATURE_UNIT, null) ?: "CELSIUS")
            }.getOrDefault(TemperatureDisplayUnit.CELSIUS),
            focuserType = focuserTypeStore.load(),
            connectionProfile = connectionProfileCatalog.defaultProfile(),
        ),
    )
    val state: StateFlow<FocuserUiState> = mutableState.asStateFlow()
    private var connectedDeviceId: Int? = null
    private var deviceRefreshJob: Job? = null
    private val usbEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> scheduleTopologyRefresh()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.usbDeviceExtra()
                    if (device != null) removeDetachedDevice(device)
                    scheduleTopologyRefresh()
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
                    it.copy(
                        focuser = focuser,
                        demoMode = if (!focuser.connected) false else it.demoMode,
                        capabilities = controller.capabilities,
                        advancedValues = if (!focuser.connected) emptyMap() else it.advancedValues,
                    )
                }
            }
        }
        refreshDevices()
    }

    fun refreshDevices() {
        startDeviceRefresh(settleDelayMs = 0, retryUnexpectedEmpty = false)
    }

    private fun scheduleTopologyRefresh() {
        startDeviceRefresh(
            settleDelayMs = USB_TOPOLOGY_SETTLE_MS,
            retryUnexpectedEmpty = true,
        )
    }

    private fun startDeviceRefresh(settleDelayMs: Long, retryUnexpectedEmpty: Boolean) {
        deviceRefreshJob?.cancel()
        deviceRefreshJob = viewModelScope.launch {
            if (settleDelayMs > 0) delay(settleDelayMs)
            val devices = try {
                scanDevices(retryUnexpectedEmpty)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A transient Android USB enumeration failure must not erase known neighbors.
                mutableState.update { it.copy(message = error.message ?: "Unable to scan USB devices") }
                return@launch
            }
            mutableState.update { old ->
                val selected = selectUsbDevice(
                    old.selectedDeviceName,
                    old.selectedDeviceIsExplicit,
                    devices.map { it.name },
                )
                old.copy(
                    devices = devices,
                    selectedDeviceName = selected.deviceName,
                    selectedDeviceIsExplicit = selected.isExplicit,
                )
            }
        }
    }

    private suspend fun scanDevices(retryUnexpectedEmpty: Boolean): List<UsbSerialDevice> {
        val first = withContext(Dispatchers.IO) { repository.listDevices() }
        if (!retryUnexpectedEmpty || first.isNotEmpty() || mutableState.value.devices.isEmpty()) {
            return first
        }
        // USB topology broadcasts can arrive before UsbManager's device list has settled.
        delay(USB_EMPTY_RETRY_MS)
        return withContext(Dispatchers.IO) { repository.listDevices() }
    }

    private fun removeDetachedDevice(device: UsbDevice) {
        mutableState.update { old ->
            val remaining = old.devices.filterNot { it.name == device.deviceName }
            val selected = selectUsbDevice(
                old.selectedDeviceName,
                old.selectedDeviceIsExplicit,
                remaining.map { it.name },
            )
            old.copy(
                devices = remaining,
                selectedDeviceName = selected.deviceName,
                selectedDeviceIsExplicit = selected.isExplicit,
            )
        }
    }

    fun selectDevice(name: String) = mutableState.update {
        it.copy(selectedDeviceName = name, selectedDeviceIsExplicit = true, message = null)
    }

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
                val serialParameters = mutableState.value.connectionProfile.serial
                val transport = withContext(Dispatchers.IO) {
                    repository.open(selected.usbDevice, serialParameters)
                }
                controller.connect(MyFocuserPro2Driver(
                    focuserType = mutableState.value.focuserType,
                    enableUnverifiedDeviceMaximumWrite = BuildConfig.ENABLE_UNVERIFIED_DEVICE_MAX_WRITE,
                    enableUnverifiedProtocolWrites = BuildConfig.ENABLE_UNVERIFIED_PROTOCOL_WRITES,
                ) { transport })
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
                controller.connect(MyFocuserPro2Driver(
                    focuserType = mutableState.value.focuserType,
                    enableUnverifiedDeviceMaximumWrite = BuildConfig.ENABLE_UNVERIFIED_DEVICE_MAX_WRITE,
                    enableUnverifiedProtocolWrites = BuildConfig.ENABLE_UNVERIFIED_PROTOCOL_WRITES,
                ) { FakeSerialTransport() })
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

    fun selectFocuserType(type: FocuserType) {
        if (mutableState.value.focuser.connected || mutableState.value.isWorking) {
            mutableState.update { it.copy(message = "Disconnect before changing the focuser type") }
            return
        }
        runCatching { focuserTypeStore.save(type) }
            .onSuccess { mutableState.update { it.copy(focuserType = type, message = "Focuser type set to ${type.displayName}") } }
            .onFailure { error -> mutableState.update { it.copy(message = error.message ?: "Unable to save focuser type") } }
    }

    fun refreshAdvancedSettings() {
        refreshSettings(FeatureCategory.ADVANCED)
    }

    fun refreshSettings(category: FeatureCategory) {
        viewModelScope.launch {
            if (!mutableState.value.focuser.connected) return@launch
            val available = controller.capabilities?.available(category)
                ?.filter { it.unavailableReason == null }
                ?.filter { it.access != CapabilityAccess.WRITE_ONLY }
                .orEmpty()
            mutableState.update { it.copy(advancedLoading = true, advancedErrors = emptyMap()) }
            val values = mutableMapOf<CapabilityId, CapabilityValue>()
            val errors = mutableMapOf<CapabilityId, String>()
            for (descriptor in available) {
                try {
                    values[descriptor.id] = controller.readCapability(descriptor.id, category)
                } catch (error: Exception) {
                    errors[descriptor.id] = error.message ?: "Unable to read ${descriptor.id}"
                }
            }
            val controllerTemperatureUnit = (values[CapabilityId.TEMPERATURE_UNIT] as? CapabilityValue.ChoiceValue)
                ?.value
                ?.let(TemperatureDisplayUnit::fromControllerValue)
            if (controllerTemperatureUnit != null && !persistTemperatureDisplayUnit(controllerTemperatureUnit)) {
                errors[CapabilityId.TEMPERATURE_UNIT] = "Unable to save temperature display preference"
            }
            mutableState.update {
                it.copy(
                    advancedLoading = false,
                    advancedValues = values,
                    advancedErrors = errors,
                    temperatureDisplayUnit = controllerTemperatureUnit ?: it.temperatureDisplayUnit,
                )
            }
        }
    }

    fun updateAdvancedSetting(id: CapabilityId, value: CapabilityValue) {
        updateCapability(id, value, FeatureCategory.ADVANCED)
    }

    fun updateCapability(id: CapabilityId, value: CapabilityValue, category: FeatureCategory) {
        viewModelScope.launch {
            try {
                val result = controller.writeCapability(id, value, category)
                val message = when {
                    category == FeatureCategory.DEVICE_ADMINISTRATION -> "Device administration command sent; completion is not confirmed"
                    id == CapabilityId.HOME -> "Home command sent; the protocol provides no completion acknowledgment"
                    id == CapabilityId.TEMPERATURE_UNIT -> "Controller temperature unit confirmed"
                    id == CapabilityId.STEP_MODE -> "Step Mode confirmed; sync the position before moving"
                    id == CapabilityId.SYNC_POSITION -> "Logical position synchronized"
                    id == CapabilityId.SET_MAX_POSITION -> "Device maximum confirmed"
                    else -> "$id setting confirmed"
                }
                val controllerTemperatureUnit = if (id == CapabilityId.TEMPERATURE_UNIT) {
                    (result as? CapabilityValue.ChoiceValue)
                        ?.value
                        ?.let(TemperatureDisplayUnit::fromControllerValue)
                } else {
                    null
                }
                val temperaturePreferenceSaved = controllerTemperatureUnit?.let(::persistTemperatureDisplayUnit) ?: true
                mutableState.update {
                    it.copy(
                        advancedValues = if (result == CapabilityValue.TriggerValue) it.advancedValues else it.advancedValues + (id to result),
                        advancedErrors = if (temperaturePreferenceSaved) {
                            it.advancedErrors - id
                        } else {
                            it.advancedErrors + (id to "Unable to save temperature display preference")
                        },
                        temperatureDisplayUnit = controllerTemperatureUnit ?: it.temperatureDisplayUnit,
                        message = message,
                    )
                }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(advancedErrors = it.advancedErrors + (id to (error.message ?: "Setting failed")))
                }
            }
        }
    }

    fun saveConnectionSettings(parameters: SerialParameters): String? {
        if (mutableState.value.focuser.connected) {
            val message = "Disconnect before changing serial settings"
            mutableState.update { it.copy(message = message) }
            return message
        }
        val result = runCatching {
            ConnectionProfileRules.validate(parameters)
            val current = mutableState.value.connectionProfile
            val updatedProfile = current.copy(serial = parameters)
            val updatedCatalog = connectionProfileCatalog.copy(
                profiles = connectionProfileCatalog.profiles.map {
                    if (it.id == current.id) updatedProfile else it
                },
            )
            ConnectionProfileRules.validate(updatedCatalog)
            connectionProfileStore.save(updatedCatalog)
            connectionProfileCatalog = updatedCatalog
            mutableState.update {
                it.copy(
                    connectionProfile = updatedProfile,
                    message = "Serial settings saved; they apply to the next connection",
                )
            }
        }
        return result.fold(
            onSuccess = { null },
            onFailure = { error ->
                val message = error.message ?: "Unable to save serial settings"
                mutableState.update { it.copy(message = message) }
                message
            },
        )
    }

    fun resetConnectionSettings() {
        if (mutableState.value.focuser.connected) {
            mutableState.update { it.copy(message = "Disconnect before resetting serial settings") }
            return
        }
        runCatching {
            val defaults = connectionProfileStore.reset()
            connectionProfileCatalog = defaults
            mutableState.update {
                it.copy(
                    connectionProfile = defaults.defaultProfile(),
                    message = "Bundled serial defaults restored",
                )
            }
        }.onFailure { error ->
            mutableState.update { it.copy(message = error.message ?: "Unable to restore serial defaults") }
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

    fun savePreset(id: String?, name: String, positionText: String): String? {
        val result = runCatching {
            val normalizedName = PresetRules.validateName(name)
            val position = positionText.toIntOrNull() ?: error("Enter a valid preset position")
            require(positionText.isNotBlank()) { "Enter a valid preset position" }
            validatePresetPosition(position)
            val current = mutableState.value.presets
            val preset = PositionPreset(id ?: UUID.randomUUID().toString(), normalizedName, position)
            val updated = if (id == null) current + preset else current.map { if (it.id == id) preset else it }
            check(id == null || current.any { it.id == id }) { "Preset no longer exists" }
            check(updated.size <= PresetRules.MAX_PRESETS) { "A maximum of ${PresetRules.MAX_PRESETS} presets is supported" }
            persistPresets(updated)
            "Preset '${preset.name}' saved"
        }
        return result.fold(
            onSuccess = { message -> mutableState.update { it.copy(message = message) }; null },
            onFailure = { error ->
                val message = error.message ?: "Unable to save preset"
                mutableState.update { it.copy(message = message) }
                message
            },
        )
    }

    fun deletePreset(id: String) {
        runCatching {
            val updated = mutableState.value.presets.filterNot { it.id == id }
            check(updated.size != mutableState.value.presets.size) { "Preset no longer exists" }
            persistPresets(updated)
        }.onFailure { error -> mutableState.update { it.copy(message = error.message ?: "Unable to delete preset") } }
    }

    fun selectPreset(preset: PositionPreset): Boolean {
        return runCatching { validatePresetPosition(preset.position) }
            .onSuccess { mutableState.update { it.copy(message = "${preset.name} loaded into Target Position") } }
            .onFailure { error -> mutableState.update { it.copy(message = error.message ?: "Preset is outside the current position limits") } }
            .isSuccess
    }

    fun exportPresets(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = PresetJsonCodec.encode(mutableState.value.presets)
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: error("Unable to open the selected export destination")
                    stream.use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
                }
                mutableState.update { it.copy(message = "Exported ${it.presets.size} presets") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(message = error.message ?: "Preset export failed") }
            }
        }
    }

    fun importPresets(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Unable to open the selected preset file")
                    stream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > PresetRules.MAX_FILE_BYTES) error("Preset file is too large")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
                val imported = PresetJsonCodec.decode(String(bytes, StandardCharsets.UTF_8))
                val limits = mutableState.value.focuser
                imported.forEach { PresetRules.validatePosition(it.position, limits.softwareMaximum, limits.deviceMaximum) }
                val merged = PresetRules.merge(mutableState.value.presets, imported)
                withContext(Dispatchers.IO) {
                    presetStore.save(merged)
                }
                mutableState.update { it.copy(presets = merged, message = "Imported and merged ${imported.size} presets") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = (error as? PresetFileException)?.message ?: error.message ?: "Preset import failed"
                mutableState.update { it.copy(message = message) }
            }
        }
    }

    private fun validatePresetPosition(position: Int) {
        val focuser = mutableState.value.focuser
        PresetRules.validatePosition(position, focuser.softwareMaximum, focuser.deviceMaximum)
    }

    private fun persistPresets(presets: List<PositionPreset>) {
        presetStore.save(presets)
        mutableState.update { it.copy(presets = presets) }
    }

    private fun launchMovement(action: suspend () -> String) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { message -> mutableState.update { it.copy(message = message) } }
                .onFailure { error -> mutableState.update { it.copy(message = error.message ?: "Move failed") } }
        }
    }

    override fun onCleared() {
        deviceRefreshJob?.cancel()
        runCatching { getApplication<Application>().unregisterReceiver(usbEventsReceiver) }
        // Close USB deterministically before ViewModel scope cancellation; no orphan cleanup scope.
        runCatching { runBlocking(Dispatchers.IO) { controller.disconnect() } }
        super.onCleared()
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }

    private companion object {
        const val USB_TOPOLOGY_SETTLE_MS = 300L
        const val USB_EMPTY_RETRY_MS = 500L
        const val KEY_POSITION_SYNC_REQUIRED = "position_sync_required_after_step_mode"
        const val KEY_TEMPERATURE_UNIT = "temperature_display_unit"
    }

    private fun persistTemperatureDisplayUnit(unit: TemperatureDisplayUnit): Boolean =
        displayPreferences.edit().putString(KEY_TEMPERATURE_UNIT, unit.name).commit()
}
