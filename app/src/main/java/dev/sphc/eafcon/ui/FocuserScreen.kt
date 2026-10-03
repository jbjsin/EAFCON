package dev.sphc.eafcon.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sphc.eafcon.R
import dev.sphc.eafcon.control.MovementState
import dev.sphc.eafcon.driver.CapabilityId
import dev.sphc.eafcon.driver.CapabilitySupport
import dev.sphc.eafcon.driver.CapabilityValue
import dev.sphc.eafcon.driver.FeatureCategory
import dev.sphc.eafcon.driver.FocuserType
import dev.sphc.eafcon.presets.PositionPreset
import dev.sphc.eafcon.presets.PresetRules
import dev.sphc.eafcon.settings.*

internal enum class AppPage { CONNECTION, CONTROL, ADVANCED, ADMINISTRATION, DEVICE_ADMINISTRATION, SETTINGS }
private enum class PresetTab { LOAD, SAVE }
private enum class PresetSaveMode { NEW, EDIT }

internal val AppPage.isPrimary: Boolean
    get() = this == AppPage.CONNECTION || this == AppPage.CONTROL

internal fun secondaryBackDestination(current: AppPage, lastPrimary: AppPage): AppPage? = when {
    current.isPrimary -> null
    lastPrimary.isPrimary -> lastPrimary
    else -> AppPage.CONNECTION
}

/** Firmware direction 0 means a temperature rise increases the target position. */
internal fun increaseOnTemperatureRiseFromProtocol(direction: Boolean): Boolean = !direction
internal fun protocolDirectionForIncreaseOnTemperatureRise(increase: Boolean): Boolean = !increase

internal fun showDeviceAdministrationAction(id: CapabilityId): Boolean =
    id != CapabilityId.RESET_CONTROLLER

@Composable
private fun AppPage.localizedLabel(): String = stringResource(
    when (this) {
        AppPage.CONNECTION -> R.string.page_connection
        AppPage.CONTROL -> R.string.page_control
        AppPage.SETTINGS -> R.string.page_settings
        AppPage.ADVANCED -> R.string.page_advanced
        AppPage.ADMINISTRATION -> R.string.page_administration
        AppPage.DEVICE_ADMINISTRATION -> R.string.page_device_administration
    },
)

@Composable
private fun MovementState.localizedLabel(): String = stringResource(
    when (this) {
        MovementState.UNKNOWN -> R.string.movement_unknown
        MovementState.IDLE -> R.string.movement_idle
        MovementState.MOVING -> R.string.movement_moving
    },
)

@Composable
private fun AppLanguage.localizedLabel(): String = stringResource(
    when (this) {
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.KOREAN -> R.string.language_korean
    },
)

@Composable
private fun localizedRuntimeMessage(raw: String): String {
    Regex("^Connected to (.+)$").matchEntire(raw)?.let {
        return stringResource(R.string.message_connected_to, it.groupValues[1])
    }
    Regex("^Software safety maximum set to (\\d+)$").matchEntire(raw)?.let {
        return stringResource(R.string.message_software_max_set, it.groupValues[1].toInt())
    }
    Regex("^Move command sent to (\\d+)$").matchEntire(raw)?.let {
        return stringResource(R.string.message_move_sent, it.groupValues[1].toInt())
    }
    Regex("^Relative move command sent \\((.+)\\)$").matchEntire(raw)?.let {
        return stringResource(R.string.message_relative_move_sent, it.groupValues[1])
    }
    Regex("^Preset '(.+)' saved$").matchEntire(raw)?.let {
        return stringResource(R.string.message_preset_saved, it.groupValues[1])
    }
    Regex("^(.+) loaded into Target Position$").matchEntire(raw)?.let {
        return stringResource(R.string.message_preset_loaded, it.groupValues[1])
    }
    Regex("^Exported (\\d+) presets$").matchEntire(raw)?.let {
        return stringResource(R.string.message_exported_presets, it.groupValues[1].toInt())
    }
        Regex("^Imported and merged (\\d+) presets$").matchEntire(raw)?.let {
            return stringResource(R.string.message_merged_presets, it.groupValues[1].toInt())
        }
        val administrativeMessages = mapOf(
            "Home command sent; the protocol provides no completion acknowledgment" to R.string.admin_action_home_sent,
            "Celsius display command sent; the protocol provides no acknowledgment" to R.string.admin_action_celsius_sent,
            "Step Mode confirmed; sync the position before moving" to R.string.admin_action_step_confirmed,
            "Logical position synchronized" to R.string.admin_action_sync_confirmed,
            "Device maximum confirmed" to R.string.admin_action_max_confirmed,
            "Controller temperature unit confirmed" to R.string.admin_action_temperature_unit_confirmed,
            "Device administration command sent; completion is not confirmed" to R.string.device_admin_sent,
        )
        administrativeMessages[raw]?.let { return stringResource(it) }
    return when (raw) {
        "USB device detached; focuser disconnected" -> stringResource(R.string.message_usb_detached)
        "Select a USB serial device first" -> stringResource(R.string.message_select_usb)
        "Requesting USB permission…" -> stringResource(R.string.message_requesting_permission)
        "USB permission was denied" -> stringResource(R.string.message_permission_denied)
        "Starting simulated focuser…" -> stringResource(R.string.message_starting_demo)
        "Demo mode: simulated focuser at 7500 steps" -> stringResource(R.string.message_demo_started)
        "Disconnected" -> stringResource(R.string.message_disconnected)
        "Disconnect before changing serial settings" -> stringResource(R.string.message_disconnect_before_serial_change)
        "Serial settings saved; they apply to the next connection" -> stringResource(R.string.message_serial_saved)
        "Disconnect before resetting serial settings" -> stringResource(R.string.message_disconnect_before_serial_reset)
        "Bundled serial defaults restored" -> stringResource(R.string.message_serial_defaults_restored)
        "Enter a valid positive software maximum" -> stringResource(R.string.message_invalid_software_max)
        "Enter a valid target position" -> stringResource(R.string.message_invalid_target)
        "Stop command sent" -> stringResource(R.string.message_stop_sent)
        "Enter a valid preset position" -> stringResource(R.string.message_invalid_preset_position)
        "Preset no longer exists" -> stringResource(R.string.message_preset_missing)
        "Current position is unknown" -> stringResource(R.string.message_current_position_unknown)
        "Connect to a focuser first" -> stringResource(R.string.message_connect_first)
        "Focuser is not idle" -> stringResource(R.string.message_focuser_not_idle)
        "Focuser is not moving" -> stringResource(R.string.message_focuser_not_moving)
        "Sync the focuser position before setting movement limits" -> stringResource(R.string.admin_position_needs_sync)
        "Sync the focuser position before moving after a Step Mode change" -> stringResource(R.string.admin_position_needs_sync)
        "Preset name cannot be empty" -> stringResource(R.string.message_preset_name_empty)
        else -> raw
    }
}

@Composable
fun FocuserScreen(
    themeMode: AppThemeMode,
    appLanguage: AppLanguage,
    vibrationSettings: MovementVibrationSettings,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onVibrationSettingsChange: (MovementVibrationSettings) -> Unit,
    viewModel: FocuserViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val focuser = ui.focuser
    var pageName by rememberSaveable { mutableStateOf(AppPage.CONNECTION.name) }
    var lastPrimaryPageName by rememberSaveable { mutableStateOf(AppPage.CONNECTION.name) }
    val page = AppPage.valueOf(pageName)
    var target by rememberSaveable { mutableStateOf("") }
    var softwareMaximum by rememberSaveable { mutableStateOf("") }
    var customStep by rememberSaveable { mutableStateOf("50") }
    var presetDialogOpen by rememberSaveable { mutableStateOf(false) }
    var presetTabName by rememberSaveable { mutableStateOf(PresetTab.LOAD.name) }
    var saveModeName by rememberSaveable { mutableStateOf(PresetSaveMode.NEW.name) }
    var presetSearch by rememberSaveable { mutableStateOf("") }
    var selectedPresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var presetName by rememberSaveable { mutableStateOf("") }
    var presetPosition by rememberSaveable { mutableStateOf("") }
    var presetEditorError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<PositionPreset?>(null) }
    val canMove = focuser.connected && focuser.movement == MovementState.IDLE &&
        !focuser.commandPending && focuser.currentPosition != null && focuser.softwareMaximum != null

    fun navigateTo(destination: AppPage) {
        if (destination == page) return
        if (destination.isPrimary) {
            lastPrimaryPageName = destination.name
        } else if (page.isPrimary) {
            lastPrimaryPageName = page.name
        }
        pageName = destination.name
    }

    val backDestination = secondaryBackDestination(page, AppPage.valueOf(lastPrimaryPageName))
    BackHandler(enabled = backDestination != null) {
        pageName = checkNotNull(backDestination).name
    }

    MovementVibrationEffect(
        moving = focuser.connected && focuser.movement == MovementState.MOVING,
        settings = vibrationSettings,
    )

    LaunchedEffect(focuser.softwareMaximum) {
        if (softwareMaximum.isBlank()) softwareMaximum = focuser.softwareMaximum?.toString().orEmpty()
    }
    LaunchedEffect(focuser.positionNeedsSync) {
        if (focuser.positionNeedsSync) {
            softwareMaximum = ""
            target = ""
        }
    }
    LaunchedEffect(focuser.connected) {
        if (focuser.connected) {
            pageName = AppPage.CONTROL.name
            lastPrimaryPageName = AppPage.CONTROL.name
        }
    }
    LaunchedEffect(page, focuser.connected) {
        when {
            page == AppPage.ADVANCED && focuser.connected -> viewModel.refreshSettings(FeatureCategory.ADVANCED)
            page == AppPage.ADMINISTRATION && focuser.connected -> viewModel.refreshSettings(FeatureCategory.ADMINISTRATIVE)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportPresets(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPresets(uri)
    }

    Scaffold(
        topBar = {
            EafconTopBar(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onNavigate = ::navigateTo,
                showAdvanced = focuser.connected && ui.capabilities?.available(FeatureCategory.ADVANCED)
                    ?.any { it.unavailableReason == null } == true,
                showAdministration = focuser.connected && ui.capabilities?.available(FeatureCategory.ADMINISTRATIVE)
                    ?.any { it.unavailableReason == null } == true,
                showDeviceAdministration = focuser.connected && ui.capabilities?.descriptors
                    ?.any { it.category == FeatureCategory.DEVICE_ADMINISTRATION } == true,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConnectionControlSwitcher(
                selectedPage = page,
                onNavigate = ::navigateTo,
            )
            ConnectionStatusPill(ui = ui, themeMode = themeMode)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (page) {
                    AppPage.CONNECTION -> ConnectionPage(ui, viewModel)
                    AppPage.CONTROL -> ControlPage(
                        ui = ui,
                        softwareMaximum = softwareMaximum,
                        onSoftwareMaximumChange = { softwareMaximum = it.filter(Char::isDigit) },
                        onSetSoftwareMaximum = { viewModel.setSoftwareMaximum(softwareMaximum) },
                        target = target,
                        onTargetChange = { target = it.filter(Char::isDigit) },
                        onMoveTo = { viewModel.moveTo(target) },
                        customStep = customStep,
                        onCustomStepChange = { customStep = it.filter(Char::isDigit) },
                        canMove = canMove,
                        onMoveBy = viewModel::moveBy,
                        onStop = viewModel::stop,
                        onOpenPresets = {
                            presetTabName = PresetTab.LOAD.name
                            saveModeName = PresetSaveMode.NEW.name
                            presetSearch = ""
                            selectedPresetId = null
                            presetName = ""
                            presetPosition = focuser.currentPosition?.toString().orEmpty()
                            presetEditorError = null
                            presetDialogOpen = true
                        },
                    )
                    AppPage.SETTINGS -> SettingsPage(
                        ui = ui,
                        viewModel = viewModel,
                        appLanguage = appLanguage,
                        onAppLanguageChange = onAppLanguageChange,
                        vibrationSettings = vibrationSettings,
                        onVibrationSettingsChange = onVibrationSettingsChange,
                    )
                    AppPage.ADVANCED -> AdvancedFocuserControlsPage(ui, viewModel)
                    AppPage.ADMINISTRATION -> AdministrativeControlsPage(ui, viewModel)
                    AppPage.DEVICE_ADMINISTRATION -> DeviceAdministrationPage(ui, viewModel)
                }
            }
        }
    }

    if (presetDialogOpen) {
        PresetsDialog(
            ui = ui,
            tab = PresetTab.valueOf(presetTabName),
            saveMode = PresetSaveMode.valueOf(saveModeName),
            search = presetSearch,
            selectedPresetId = selectedPresetId,
            presetName = presetName,
            presetPosition = presetPosition,
            editorError = presetEditorError,
            onDismiss = { presetDialogOpen = false },
            onTabChange = { selected ->
                presetTabName = selected.name
                selectedPresetId = null
                presetEditorError = null
                if (selected == PresetTab.SAVE) {
                    saveModeName = PresetSaveMode.NEW.name
                    presetName = ""
                    presetPosition = focuser.currentPosition?.toString().orEmpty()
                }
            },
            onSaveModeChange = { selected ->
                saveModeName = selected.name
                presetEditorError = null
                if (selected == PresetSaveMode.NEW) {
                    selectedPresetId = null
                    presetName = ""
                    presetPosition = focuser.currentPosition?.toString().orEmpty()
                } else {
                    val first = ui.presets.firstOrNull()
                    selectedPresetId = first?.id
                    presetName = first?.name.orEmpty()
                    presetPosition = first?.position?.toString().orEmpty()
                }
            },
            onSearchChange = { presetSearch = it },
            onSelectPreset = { preset ->
                selectedPresetId = preset.id
                if (PresetSaveMode.valueOf(saveModeName) == PresetSaveMode.EDIT) {
                    presetName = preset.name
                    presetPosition = preset.position.toString()
                }
            },
            onPresetNameChange = { if (it.length <= PresetRules.MAX_NAME_LENGTH) presetName = it },
            onPresetPositionChange = { presetPosition = it.filter(Char::isDigit) },
            onLoad = {
                val preset = ui.presets.firstOrNull { it.id == selectedPresetId }
                if (preset != null && viewModel.selectPreset(preset)) {
                    target = preset.position.toString()
                    presetDialogOpen = false
                }
            },
            onSave = {
                val id = if (PresetSaveMode.valueOf(saveModeName) == PresetSaveMode.EDIT) selectedPresetId else null
                val error = viewModel.savePreset(id, presetName, presetPosition)
                if (error == null) presetDialogOpen = false else presetEditorError = error
            },
            onDelete = { deleteTarget = ui.presets.firstOrNull { it.id == selectedPresetId } },
            onImport = {
                importLauncher.launch(arrayOf("application/json", "text/json", "*/*"))
            },
            onExport = { exportLauncher.launch("EAFCon_Presets.json") },
        )
    }

    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_preset_title)) },
            text = { Text(stringResource(R.string.delete_preset_message, preset.name, preset.position)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePreset(preset.id)
                    deleteTarget = null
                    presetDialogOpen = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EafconTopBar(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onNavigate: (AppPage) -> Unit,
    showAdvanced: Boolean,
    showAdministration: Boolean,
    showDeviceAdministration: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("EAFCON", fontWeight = FontWeight.Bold) },
        actions = {
            TextButton(onClick = { onThemeModeChange(themeMode.next()) }) {
                Text(when (themeMode) {
                    AppThemeMode.LIGHT -> "☀"
                    AppThemeMode.DARK -> "◐"
                    AppThemeMode.NIGHT -> "●"
                })
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Text("☰", style = MaterialTheme.typography.titleLarge) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AppPage.entries.filter { destination ->
                        when (destination) {
                            AppPage.ADVANCED -> showAdvanced
                            AppPage.ADMINISTRATION -> showAdministration
                            AppPage.DEVICE_ADMINISTRATION -> showDeviceAdministration
                            else -> true
                        }
                    }.forEach { destination ->
                        DropdownMenuItem(
                            text = { Text(destination.localizedLabel()) },
                            onClick = {
                                menuOpen = false
                                onNavigate(destination)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ConnectionControlSwitcher(
    selectedPage: AppPage,
    onNavigate: (AppPage) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(AppPage.CONNECTION, AppPage.CONTROL).forEach { destination ->
                if (selectedPage == destination) {
                    Button(
                        onClick = { onNavigate(destination) },
                        modifier = Modifier.weight(1f),
                    ) { Text(destination.localizedLabel()) }
                } else {
                    OutlinedButton(
                        onClick = { onNavigate(destination) },
                        modifier = Modifier.weight(1f),
                    ) { Text(destination.localizedLabel()) }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(ui: FocuserUiState, themeMode: AppThemeMode) {
    val connected = ui.focuser.connected
    val text = when {
        !connected -> stringResource(R.string.status_disconnected)
        ui.demoMode -> stringResource(R.string.status_connected_demo)
        else -> stringResource(R.string.status_connected_usb)
    }
    val movementLabel = ui.focuser.movement.localizedLabel()
    val telemetry = buildString {
        append("P ")
        append(ui.focuser.currentPosition ?: "—")
        append(" · ")
        append(formatTemperature(ui.focuser.temperatureCelsius, ui.temperatureDisplayUnit).replace(" ", ""))
        append(" · ")
        append(movementLabel)
    }
    val container = when {
        themeMode == AppThemeMode.NIGHT -> MaterialTheme.colorScheme.primaryContainer
        connected -> Color(0xFFDCEFE2)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        themeMode == AppThemeMode.NIGHT -> MaterialTheme.colorScheme.onPrimaryContainer
        connected -> Color(0xFF0A6A34)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("●", style = MaterialTheme.typography.labelSmall)
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                telemetry,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConnectionPage(ui: FocuserUiState, viewModel: FocuserViewModel) {
    val focuser = ui.focuser
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.focuser_type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.focuser_type_note), style = MaterialTheme.typography.bodySmall)
                FocuserType.entries.forEach { type ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !focuser.connected && !ui.isWorking) {
                            viewModel.selectFocuserType(type)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = ui.focuserType == type,
                            onClick = { viewModel.selectFocuserType(type) },
                            enabled = !focuser.connected && !ui.isWorking,
                        )
                        Column {
                            Text(type.displayName, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(if (type == FocuserType.GEMINI_FOCUSER_PRO) R.string.focuser_type_gemini_note else R.string.focuser_type_generic_note),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.usb_devices), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (ui.devices.isEmpty()) stringResource(R.string.no_compatible_devices)
                    else pluralStringResource(R.plurals.compatible_devices_found, ui.devices.size, ui.devices.size),
                    style = MaterialTheme.typography.bodySmall,
                )
                ui.devices.forEach { device ->
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = !focuser.connected) {
                            viewModel.selectDevice(device.name)
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = ui.selectedDeviceName == device.name,
                                onClick = { viewModel.selectDevice(device.name) },
                                enabled = !focuser.connected,
                            )
                            Column {
                                Text(device.stableLabel, fontWeight = FontWeight.Medium)
                                Text(device.displayInfo.driverName ?: stringResource(R.string.usb_serial), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (ui.selectedDeviceName == device.name) {
                            Column(Modifier.padding(start = 48.dp, bottom = 8.dp)) {
                                device.displayInfo.detailLines().forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(stringResource(R.string.temporary_bus_details), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Text(stringResource(R.string.select_device_before_connecting), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = viewModel::refreshDevices,
                        enabled = !ui.isWorking && !focuser.connected,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_scan)) }
                    Button(
                        onClick = if (focuser.connected) viewModel::disconnect else viewModel::connect,
                        enabled = if (focuser.connected) !ui.isWorking else !ui.isWorking && ui.selectedDeviceName != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(if (focuser.connected) R.string.action_disconnect else R.string.action_connect))
                    }
                }
                OutlinedButton(
                    onClick = viewModel::connectDemo,
                    enabled = !focuser.connected && !ui.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_demo_mode)) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.connection_notes), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.scan_note), style = MaterialTheme.typography.bodySmall)
                val serial = ui.connectionProfile.serial
                Text(
                    stringResource(
                        R.string.serial_summary,
                        serial.baudRate,
                        serial.dataBits,
                        serial.parity.name.first().toString(),
                        serial.stopBits.displayName,
                        serial.flowControl.displayName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        UiMessages(ui)
    }
}

@Composable
private fun ControlPage(
    ui: FocuserUiState,
    softwareMaximum: String,
    onSoftwareMaximumChange: (String) -> Unit,
    onSetSoftwareMaximum: () -> Unit,
    target: String,
    onTargetChange: (String) -> Unit,
    onMoveTo: () -> Unit,
    customStep: String,
    onCustomStepChange: (String) -> Unit,
    canMove: Boolean,
    onMoveBy: (Int) -> Unit,
    onStop: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val focuser = ui.focuser
    val customValue = customStep.toIntOrNull()?.takeIf { it > 0 }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.current_position), style = MaterialTheme.typography.labelLarge)
                Text(focuser.currentPosition?.toString() ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                StatusRow(stringResource(R.string.device_max), focuser.deviceMaximum?.toString() ?: "—")
                StatusRow(stringResource(R.string.software_limit), focuser.softwareMaximum?.toString() ?: stringResource(R.string.not_set))
                StatusRow(stringResource(R.string.temperature), formatTemperature(focuser.temperatureCelsius, ui.temperatureDisplayUnit))
                StatusRow(stringResource(R.string.movement), focuser.movement.localizedLabel())
            }
        }
        CompactNumberAction(stringResource(R.string.software_limit), softwareMaximum, onSoftwareMaximumChange, stringResource(R.string.action_set), onSetSoftwareMaximum,
            focuser.connected && !focuser.commandPending && !focuser.positionNeedsSync && softwareMaximum.isNotBlank())
        CompactNumberAction(stringResource(R.string.target_position), target, onTargetChange, stringResource(R.string.action_go), onMoveTo, canMove && target.isNotBlank())
        Button(
            onClick = onStop,
            enabled = focuser.connected && focuser.movement == MovementState.MOVING,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.action_stop)) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.relative_move), fontWeight = FontWeight.Bold)
                listOf(5, 25, 50, 100).forEach { step ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onMoveBy(-step) }, enabled = canMove, modifier = Modifier.weight(1f)) { Text("−$step") }
                        Button(onClick = { onMoveBy(step) }, enabled = canMove, modifier = Modifier.weight(1f)) { Text("+$step") }
                    }
                }
                Text(stringResource(R.string.custom_steps), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { customValue?.let { onMoveBy(-it) } },
                        enabled = canMove && customValue != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("−") }
                    OutlinedTextField(
                        value = customStep,
                        onValueChange = onCustomStepChange,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.85f),
                    )
                    Button(
                        onClick = { customValue?.let { onMoveBy(it) } },
                        enabled = canMove && customValue != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("+") }
                }
            }
        }
        Button(onClick = onOpenPresets, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_presets)) }
        UiMessages(ui)
    }
}

@Composable
private fun AdvancedFocuserControlsPage(ui: FocuserUiState, viewModel: FocuserViewModel) {
    val descriptors = ui.capabilities?.available(FeatureCategory.ADVANCED)
        ?.filter { it.unavailableReason == null }
        .orEmpty()
    val enabled = ui.focuser.connected && ui.focuser.movement == MovementState.IDLE && !ui.focuser.commandPending
    var backlashInText by rememberSaveable { mutableStateOf("0") }
    var backlashOutText by rememberSaveable { mutableStateOf("0") }
    var temperatureCoefficientText by rememberSaveable { mutableStateOf("0") }
    var temperatureDirectionMenuOpen by remember { mutableStateOf(false) }
    val backlashIn = ui.advancedValues[CapabilityId.BACKLASH_IN] as? CapabilityValue.BacklashValue
    val backlashOut = ui.advancedValues[CapabilityId.BACKLASH_OUT] as? CapabilityValue.BacklashValue
    val temperatureCompensation = (ui.advancedValues[CapabilityId.TEMPERATURE_COMPENSATION] as? CapabilityValue.BooleanValue)?.value
    val temperatureDirection = (ui.advancedValues[CapabilityId.TEMPERATURE_COMPENSATION_DIRECTION] as? CapabilityValue.BooleanValue)?.value
    val temperatureCoefficient = (ui.advancedValues[CapabilityId.TEMPERATURE_COMPENSATION_COEFFICIENT] as? CapabilityValue.IntegerValue)?.value

    LaunchedEffect(backlashIn?.steps) { backlashIn?.let { backlashInText = it.steps.toString() } }
    LaunchedEffect(backlashOut?.steps) { backlashOut?.let { backlashOutText = it.steps.toString() } }
    LaunchedEffect(temperatureCoefficient) { temperatureCoefficient?.let { temperatureCoefficientText = it.toString() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.advanced_controls_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.advanced_controls_description), style = MaterialTheme.typography.bodySmall)
                if (ui.advancedLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!ui.focuser.connected) Text(stringResource(R.string.advanced_requires_connection))
                if (descriptors.isEmpty() && ui.focuser.connected) Text(stringResource(R.string.no_advanced_capabilities))
                OutlinedButton(onClick = viewModel::refreshAdvancedSettings, enabled = ui.focuser.connected) {
                    Text(stringResource(R.string.action_refresh))
                }
            }
        }

        if (descriptors.any { it.id == CapabilityId.REVERSE || it.id == CapabilityId.MOTOR_SPEED }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.advanced_motion), fontWeight = FontWeight.Bold)
                    if (descriptors.any { it.id == CapabilityId.REVERSE }) {
                        val value = (ui.advancedValues[CapabilityId.REVERSE] as? CapabilityValue.BooleanValue)?.value
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.advanced_reverse))
                                ui.advancedErrors[CapabilityId.REVERSE]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            }
                            Switch(
                                checked = value ?: false,
                                onCheckedChange = { viewModel.updateAdvancedSetting(CapabilityId.REVERSE, CapabilityValue.BooleanValue(it)) },
                                enabled = enabled && value != null,
                            )
                        }
                    }
                    if (descriptors.any { it.id == CapabilityId.MOTOR_SPEED }) {
                        val speed = (ui.advancedValues[CapabilityId.MOTOR_SPEED] as? CapabilityValue.IntegerValue)?.value
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.advanced_motor_speed), Modifier.weight(1f))
                            TextButton(onClick = {
                                val next = if (speed == null || speed >= 2) 0 else speed + 1
                                viewModel.updateAdvancedSetting(CapabilityId.MOTOR_SPEED, CapabilityValue.IntegerValue(next))
                            }, enabled = enabled && speed != null) {
                                Text(stringResource(when (speed) { 0 -> R.string.speed_slow; 1 -> R.string.speed_medium; 2 -> R.string.speed_fast; else -> R.string.not_set }))
                            }
                        }
                        ui.advancedErrors[CapabilityId.MOTOR_SPEED]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        if (descriptors.any { it.id == CapabilityId.BACKLASH_IN || it.id == CapabilityId.BACKLASH_OUT }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.advanced_backlash), fontWeight = FontWeight.Bold)
                    if (descriptors.any { it.id == CapabilityId.BACKLASH_IN }) {
                        BacklashControl(
                            title = stringResource(R.string.backlash_in),
                            enabledValue = backlashIn,
                            stepsText = backlashInText,
                            onStepsChange = { backlashInText = it.filter(Char::isDigit).take(3) },
                            error = ui.advancedErrors[CapabilityId.BACKLASH_IN],
                            enabled = enabled,
                            onSave = { flag, steps -> viewModel.updateAdvancedSetting(CapabilityId.BACKLASH_IN, CapabilityValue.BacklashValue(flag, steps)) },
                        )
                    }
                    if (descriptors.any { it.id == CapabilityId.BACKLASH_OUT }) {
                        BacklashControl(
                            title = stringResource(R.string.backlash_out),
                            enabledValue = backlashOut,
                            stepsText = backlashOutText,
                            onStepsChange = { backlashOutText = it.filter(Char::isDigit).take(3) },
                            error = ui.advancedErrors[CapabilityId.BACKLASH_OUT],
                            enabled = enabled,
                            onSave = { flag, steps -> viewModel.updateAdvancedSetting(CapabilityId.BACKLASH_OUT, CapabilityValue.BacklashValue(flag, steps)) },
                        )
                    }
                }
            }
        }
        if (descriptors.any { it.id in setOf(CapabilityId.TEMPERATURE_COMPENSATION, CapabilityId.TEMPERATURE_COMPENSATION_COEFFICIENT, CapabilityId.TEMPERATURE_COMPENSATION_DIRECTION) }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.advanced_temperature_compensation), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.advanced_temperature_compensation_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (descriptors.any { it.id == CapabilityId.TEMPERATURE_COMPENSATION }) {
                        AdminSwitch(stringResource(R.string.advanced_temperature_compensation_enabled), temperatureCompensation, enabled) {
                            viewModel.updateAdvancedSetting(CapabilityId.TEMPERATURE_COMPENSATION, CapabilityValue.BooleanValue(it))
                        }
                    }
                    if (descriptors.any { it.id == CapabilityId.TEMPERATURE_COMPENSATION_DIRECTION }) {
                        Text(stringResource(R.string.advanced_temperature_compensation_relation))
                        Box(Modifier.fillMaxWidth()) {
                            val increaseOnRise = temperatureDirection?.let(::increaseOnTemperatureRiseFromProtocol)
                            OutlinedButton(
                                onClick = { temperatureDirectionMenuOpen = true },
                                enabled = enabled && increaseOnRise != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    when (increaseOnRise) {
                                        true -> stringResource(R.string.advanced_temperature_relation_same)
                                        false -> stringResource(R.string.advanced_temperature_relation_inverse)
                                        null -> stringResource(R.string.not_set)
                                    },
                                )
                            }
                            DropdownMenu(
                                expanded = temperatureDirectionMenuOpen,
                                onDismissRequest = { temperatureDirectionMenuOpen = false },
                            ) {
                                listOf(
                                    true to R.string.advanced_temperature_relation_same,
                                    false to R.string.advanced_temperature_relation_inverse,
                                ).forEach { (selectedIncreaseOnRise, label) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        onClick = {
                                            temperatureDirectionMenuOpen = false
                                            viewModel.updateAdvancedSetting(
                                                CapabilityId.TEMPERATURE_COMPENSATION_DIRECTION,
                                                CapabilityValue.BooleanValue(
                                                    protocolDirectionForIncreaseOnTemperatureRise(selectedIncreaseOnRise),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (descriptors.any { it.id == CapabilityId.TEMPERATURE_COMPENSATION_COEFFICIENT }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = temperatureCoefficientText,
                                onValueChange = { temperatureCoefficientText = it.filter(Char::isDigit).take(4) },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.advanced_temperature_coefficient,
                                            ui.temperatureDisplayUnit.degreeSymbol,
                                        ),
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { temperatureCoefficientText.toIntOrNull()?.let { viewModel.updateAdvancedSetting(CapabilityId.TEMPERATURE_COMPENSATION_COEFFICIENT, CapabilityValue.IntegerValue(it)) } },
                                enabled = enabled && temperatureCoefficientText.toIntOrNull()?.let { it in 0..1000 } == true,
                            ) { Text(stringResource(R.string.action_set)) }
                        }
                    }
                }
            }
        }
        UiMessages(ui)
    }
}

private data class PendingAdminAction(val id: CapabilityId, val value: CapabilityValue)

@Composable
private fun AdministrativeControlsPage(ui: FocuserUiState, viewModel: FocuserViewModel) {
    val category = FeatureCategory.ADMINISTRATIVE
    val descriptors = ui.capabilities?.available(category)?.filter { it.unavailableReason == null }.orEmpty()
    val ids = descriptors.map { it.id }.toSet()
    val enabled = ui.focuser.connected && ui.focuser.movement == MovementState.IDLE && !ui.focuser.commandPending
    var syncText by rememberSaveable { mutableStateOf("") }
    var deviceMaximumText by rememberSaveable { mutableStateOf("") }
    var stepMenuOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingAdminAction?>(null) }
    val stepMode = (ui.advancedValues[CapabilityId.STEP_MODE] as? CapabilityValue.IntegerValue)?.value
    val coilPower = (ui.advancedValues[CapabilityId.COIL_POWER] as? CapabilityValue.BooleanValue)?.value
    val displayEnabled = (ui.advancedValues[CapabilityId.DISPLAY_CONFIGURATION] as? CapabilityValue.BooleanValue)?.value
    val controllerTemperatureUnit = (ui.advancedValues[CapabilityId.TEMPERATURE_UNIT] as? CapabilityValue.ChoiceValue)?.value
    val geminiHasNoLcd = ui.focuserType == FocuserType.GEMINI_FOCUSER_PRO

    LaunchedEffect(ui.focuser.currentPosition) {
        if (syncText.isBlank()) syncText = ui.focuser.currentPosition?.toString().orEmpty()
    }
    LaunchedEffect(ui.focuser.deviceMaximum) {
        if (deviceMaximumText.isBlank()) deviceMaximumText = ui.focuser.deviceMaximum?.toString().orEmpty()
    }
    val dialogText = when (pending?.id) {
        CapabilityId.STEP_MODE -> R.string.admin_confirm_step
        CapabilityId.SYNC_POSITION -> R.string.admin_confirm_sync
        CapabilityId.SET_MAX_POSITION -> R.string.admin_confirm_max
        CapabilityId.COIL_POWER -> R.string.admin_confirm_coil
        CapabilityId.HOME -> R.string.admin_confirm_home
        CapabilityId.TEMPERATURE_UNIT -> R.string.admin_confirm_temperature_unit
        else -> null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.administration_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.administration_description), style = MaterialTheme.typography.bodySmall)
                if (ui.focuser.positionNeedsSync) {
                    Text(stringResource(R.string.admin_position_needs_sync), color = MaterialTheme.colorScheme.error)
                }
                if (ui.advancedLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (descriptors.isEmpty()) Text(stringResource(R.string.no_admin_capabilities))
                OutlinedButton(onClick = { viewModel.refreshSettings(category) }, enabled = ui.focuser.connected) {
                    Text(stringResource(R.string.action_refresh))
                }
            }
        }

        if (CapabilityId.SYNC_POSITION in ids) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.administration_position_display), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.admin_position_display_description), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = syncText,
                        onValueChange = { syncText = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.admin_current_position_display)) },
                        placeholder = { Text(stringResource(R.string.admin_enter_value)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { syncText.toIntOrNull()?.let { pending = PendingAdminAction(CapabilityId.SYNC_POSITION, CapabilityValue.IntegerValue(it)) } },
                        enabled = enabled && syncText.toIntOrNull() != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.admin_change_position_display)) }
                }
            }
        }

        if (CapabilityId.STEP_MODE in ids) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.administration_position_scale), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.admin_step_multiplier_description), style = MaterialTheme.typography.bodySmall)
                    Box {
                        OutlinedButton(onClick = { stepMenuOpen = true }, enabled = enabled && !ui.focuser.positionNeedsSync) {
                            Text(stepMode?.let { "×$it" } ?: stringResource(R.string.not_set))
                        }
                        DropdownMenu(expanded = stepMenuOpen, onDismissRequest = { stepMenuOpen = false }) {
                            listOf(1, 2, 4, 8, 16, 32, 64, 128, 256).forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text("×$mode") },
                                    onClick = {
                                        stepMenuOpen = false
                                        pending = PendingAdminAction(CapabilityId.STEP_MODE, CapabilityValue.IntegerValue(mode))
                                    },
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.admin_step_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (CapabilityId.SET_MAX_POSITION in ids) {
            val requestedMaximum = deviceMaximumText.toIntOrNull()
            val validMaximum = requestedMaximum != null &&
                requestedMaximum >= (ui.focuser.currentPosition ?: Int.MAX_VALUE) &&
                requestedMaximum >= (ui.focuser.softwareMaximum ?: 0)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.administration_device_maximum), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.admin_device_maximum_description), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = deviceMaximumText,
                        onValueChange = { deviceMaximumText = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.device_max)) },
                        placeholder = { Text(stringResource(R.string.admin_enter_value)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            requestedMaximum?.let {
                                pending = PendingAdminAction(CapabilityId.SET_MAX_POSITION, CapabilityValue.IntegerValue(it))
                            }
                        },
                        enabled = enabled && !ui.focuser.positionNeedsSync && validMaximum,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.admin_set_device_max)) }
                }
            }
        }

        if (descriptors.any { it.id in setOf(CapabilityId.DISPLAY_CONFIGURATION, CapabilityId.TEMPERATURE_UNIT) }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.administration_display_options), fontWeight = FontWeight.Bold)
                    if (CapabilityId.DISPLAY_CONFIGURATION in ids) {
                        AdminSwitch(stringResource(R.string.admin_display), displayEnabled, enabled && !geminiHasNoLcd) {
                            viewModel.updateCapability(CapabilityId.DISPLAY_CONFIGURATION, CapabilityValue.BooleanValue(it), category)
                        }
                        if (geminiHasNoLcd) Text(stringResource(R.string.admin_gemini_no_lcd), style = MaterialTheme.typography.bodySmall)
                    }
                    if (CapabilityId.TEMPERATURE_UNIT in ids) {
                        Text(stringResource(R.string.admin_controller_temperature_unit))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("CELSIUS" to R.string.temperature_unit_celsius, "FAHRENHEIT" to R.string.temperature_unit_fahrenheit).forEach { (unit, label) ->
                                val action = { pending = PendingAdminAction(CapabilityId.TEMPERATURE_UNIT, CapabilityValue.ChoiceValue(unit)) }
                                if (controllerTemperatureUnit == unit) Button(onClick = action, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(label)) }
                                else OutlinedButton(onClick = action, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(label)) }
                            }
                        }
                    }
                }
            }
        }

        if (descriptors.any { it.id in setOf(CapabilityId.COIL_POWER, CapabilityId.HOME) }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.administration_motor_control), fontWeight = FontWeight.Bold)
                    if (CapabilityId.COIL_POWER in ids) {
                        AdminSwitch(stringResource(R.string.admin_coil_power), coilPower, enabled) {
                            pending = PendingAdminAction(CapabilityId.COIL_POWER, CapabilityValue.BooleanValue(it))
                        }
                        Text(
                            stringResource(R.string.admin_coil_power_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (CapabilityId.HOME in ids) {
                        Button(
                            onClick = { pending = PendingAdminAction(CapabilityId.HOME, CapabilityValue.TriggerValue) },
                            enabled = enabled && !ui.focuser.positionNeedsSync && ui.focuser.currentPosition != null && ui.focuser.softwareMaximum != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.admin_home)) }
                    }
                }
            }
        }
        UiMessages(ui)
    }

    if (pending != null && dialogText != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.admin_confirm_title)) },
            text = { Text(stringResource(dialogText)) },
            confirmButton = {
                TextButton(onClick = {
                    val action = pending
                    pending = null
                    if (action != null) viewModel.updateCapability(action.id, action.value, category)
                }) { Text(stringResource(R.string.admin_apply)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AdminSwitch(label: String, checked: Boolean?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(checked = checked ?: false, onCheckedChange = onChange, enabled = enabled && checked != null)
    }
}

@Composable
private fun DeviceAdministrationPage(ui: FocuserUiState, viewModel: FocuserViewModel) {
    val descriptors = ui.capabilities?.descriptors
        ?.filter { it.category == FeatureCategory.DEVICE_ADMINISTRATION }
        ?.filter { showDeviceAdministrationAction(it.id) }
        .orEmpty()
    val canExecute = ui.focuser.connected && ui.focuser.movement == MovementState.IDLE && !ui.focuser.commandPending
    var pendingId by remember { mutableStateOf<CapabilityId?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.device_admin_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.device_admin_page_description), style = MaterialTheme.typography.bodySmall)
            }
        }
        descriptors.forEach { descriptor ->
            val available = descriptor.support == CapabilitySupport.SUPPORTED && descriptor.unavailableReason == null
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(deviceAdminTitleRes(descriptor.id)), fontWeight = FontWeight.Bold)
                    Text(stringResource(deviceAdminDescriptionRes(descriptor.id)), style = MaterialTheme.typography.bodySmall)
                    if (!available) {
                        Text(
                            descriptor.unavailableReason ?: stringResource(R.string.device_admin_unavailable),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { pendingId = descriptor.id },
                        enabled = available && canExecute,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(deviceAdminActionRes(descriptor.id))) }
                }
            }
        }
        if (descriptors.isEmpty()) Text(stringResource(R.string.device_admin_unavailable), color = MaterialTheme.colorScheme.error)
        UiMessages(ui)
    }
    pendingId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingId = null },
            title = { Text(stringResource(deviceAdminTitleRes(id))) },
            text = {
                Text("${stringResource(deviceAdminDescriptionRes(id))}\n\n${stringResource(R.string.device_admin_confirm)}")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingId = null
                    viewModel.updateCapability(id, CapabilityValue.TriggerValue, FeatureCategory.DEVICE_ADMINISTRATION)
                }) { Text(stringResource(deviceAdminActionRes(id))) }
            },
            dismissButton = { TextButton(onClick = { pendingId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun deviceAdminTitleRes(id: CapabilityId): Int = when (id) {
    CapabilityId.PERSIST_SETTINGS -> R.string.device_admin_persist_title
    CapabilityId.RESTORE_DEFAULTS -> R.string.device_admin_restore_title
    else -> R.string.device_admin_title
}

private fun deviceAdminDescriptionRes(id: CapabilityId): Int = when (id) {
    CapabilityId.PERSIST_SETTINGS -> R.string.device_admin_persist_description
    CapabilityId.RESTORE_DEFAULTS -> R.string.device_admin_restore_description
    else -> R.string.device_admin_page_description
}

private fun deviceAdminActionRes(id: CapabilityId): Int = when (id) {
    CapabilityId.PERSIST_SETTINGS -> R.string.device_admin_persist_action
    CapabilityId.RESTORE_DEFAULTS -> R.string.device_admin_restore_action
    else -> R.string.device_admin_execute
}

@Composable
private fun BacklashControl(
    title: String,
    enabledValue: CapabilityValue.BacklashValue?,
    stepsText: String,
    onStepsChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    onSave: (Boolean, Int) -> Unit,
) {
    var localEnabled by remember(title) { mutableStateOf(false) }
    LaunchedEffect(enabledValue?.enabled) { enabledValue?.let { localEnabled = it.enabled } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            Switch(
                checked = localEnabled,
                onCheckedChange = { localEnabled = it; stepsText.toIntOrNull()?.let { value -> onSave(it, value) } },
                enabled = enabled && enabledValue != null,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = stepsText,
                onValueChange = onStepsChange,
                label = { Text(stringResource(R.string.backlash_steps)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            Button(onClick = {
                stepsText.toIntOrNull()?.takeIf { it in 0..255 }?.let { onSave(localEnabled, it) }
            }, enabled = enabled && enabledValue != null && stepsText.toIntOrNull()?.let { it in 0..255 } == true) {
                Text(stringResource(R.string.action_set))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SettingsPage(
    ui: FocuserUiState,
    viewModel: FocuserViewModel,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    vibrationSettings: MovementVibrationSettings,
    onVibrationSettingsChange: (MovementVibrationSettings) -> Unit,
) {
    val profile = ui.connectionProfile
    val serial = profile.serial
    var baudRate by rememberSaveable { mutableStateOf(serial.baudRate.toString()) }
    var dataBits by rememberSaveable { mutableIntStateOf(serial.dataBits) }
    var stopBitsName by rememberSaveable { mutableStateOf(serial.stopBits.name) }
    var parityName by rememberSaveable { mutableStateOf(serial.parity.name) }
    var flowControlName by rememberSaveable { mutableStateOf(serial.flowControl.name) }
    var readTimeout by rememberSaveable { mutableStateOf(serial.readTimeoutMs.toString()) }
    var writeTimeout by rememberSaveable { mutableStateOf(serial.writeTimeoutMs.toString()) }
    var responseTimeout by rememberSaveable { mutableStateOf(serial.responseTimeoutMs.toString()) }
    var localError by remember { mutableStateOf<String?>(null) }
    val editable = !ui.focuser.connected
    val languageOptions = listOf(
        AppLanguage.ENGLISH to AppLanguage.ENGLISH.localizedLabel(),
        AppLanguage.KOREAN to AppLanguage.KOREAN.localizedLabel(),
    )
    val previewVibration = rememberMovementVibrationPreview()
    val invalidBaudRate = stringResource(R.string.invalid_baud_rate)
    val invalidReadTimeout = stringResource(R.string.invalid_read_timeout)
    val invalidWriteTimeout = stringResource(R.string.invalid_write_timeout)
    val invalidResponseTimeout = stringResource(R.string.invalid_response_timeout)
    val invalidSerialSettings = stringResource(R.string.invalid_serial_settings)

    LaunchedEffect(serial) {
        baudRate = serial.baudRate.toString()
        dataBits = serial.dataBits
        stopBitsName = serial.stopBits.name
        parityName = serial.parity.name
        flowControlName = serial.flowControl.name
        readTimeout = serial.readTimeoutMs.toString()
        writeTimeout = serial.writeTimeoutMs.toString()
        responseTimeout = serial.responseTimeoutMs.toString()
        localError = null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.language_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ChoiceSetting(
                    label = stringResource(R.string.language_label),
                    value = appLanguage.localizedLabel(),
                    options = languageOptions.map { it.second },
                    enabled = true,
                ) { selectedLabel ->
                    languageOptions.firstOrNull { it.second == selectedLabel }?.first?.let(onAppLanguageChange)
                }
                Text(stringResource(R.string.language_description), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.movement_vibration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.vibrate_while_moving), modifier = Modifier.weight(1f))
                    Switch(
                        checked = vibrationSettings.enabled,
                        onCheckedChange = {
                            onVibrationSettingsChange(vibrationSettings.copy(enabled = it))
                        },
                    )
                }
                Text(stringResource(R.string.movement_vibration_description), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(stringResource(R.string.vibration_strength), fontWeight = FontWeight.Medium)
                SegmentedButtons(
                    options = VibrationStrength.entries.map { it to it.level.toString() },
                    selected = vibrationSettings.strength,
                    onSelected = { selected ->
                        previewVibration(
                            selected,
                            vibrationSettings.enabled && ui.focuser.connected &&
                                ui.focuser.movement == MovementState.MOVING,
                        )
                        onVibrationSettingsChange(vibrationSettings.copy(strength = selected))
                    },
                )
                Text(
                    stringResource(
                        R.string.vibration_strength_selected,
                        vibrationSettings.strength.level,
                        vibrationSettings.strength.percent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.vibration_hardware_note), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.serial_connection), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(profile.name)
                Text(stringResource(R.string.serial_apply_note), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                NumberSetting(stringResource(R.string.baud_rate), baudRate, { baudRate = it.filter(Char::isDigit) }, editable)
                ChoiceSetting(stringResource(R.string.data_bits), dataBits.toString(), (5..8).map { it.toString() }, editable) { dataBits = it.toInt() }
                ChoiceSetting(
                    stringResource(R.string.stop_bits), SerialStopBits.valueOf(stopBitsName).displayName,
                    SerialStopBits.entries.map { it.displayName }, editable,
                ) { label -> stopBitsName = SerialStopBits.entries.first { it.displayName == label }.name }
                ChoiceSetting(stringResource(R.string.parity), parityName, SerialParity.entries.map { it.name }, editable) { parityName = it }
                ChoiceSetting(
                    stringResource(R.string.flow_control), SerialFlowControl.valueOf(flowControlName).displayName,
                    SerialFlowControl.entries.map { it.displayName }, editable,
                ) { label -> flowControlName = SerialFlowControl.entries.first { it.displayName == label }.name }
                HorizontalDivider()
                Text(stringResource(R.string.timeouts), fontWeight = FontWeight.Bold)
                NumberSetting(stringResource(R.string.read_timeout_ms), readTimeout, { readTimeout = it.filter(Char::isDigit) }, editable)
                NumberSetting(stringResource(R.string.write_timeout_ms), writeTimeout, { writeTimeout = it.filter(Char::isDigit) }, editable)
                NumberSetting(stringResource(R.string.response_timeout_ms), responseTimeout, { responseTimeout = it.filter(Char::isDigit) }, editable)
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = viewModel::resetConnectionSettings,
                        enabled = editable,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_reset)) }
                    Button(
                        onClick = {
                            localError = runCatching {
                                SerialParameters(
                                    baudRate = baudRate.toIntOrNull() ?: error(invalidBaudRate),
                                    dataBits = dataBits,
                                    stopBits = SerialStopBits.valueOf(stopBitsName),
                                    parity = SerialParity.valueOf(parityName),
                                    flowControl = SerialFlowControl.valueOf(flowControlName),
                                    readTimeoutMs = readTimeout.toIntOrNull() ?: error(invalidReadTimeout),
                                    writeTimeoutMs = writeTimeout.toIntOrNull() ?: error(invalidWriteTimeout),
                                    responseTimeoutMs = responseTimeout.toIntOrNull() ?: error(invalidResponseTimeout),
                                )
                            }.fold(
                                onSuccess = viewModel::saveConnectionSettings,
                                onFailure = { it.message ?: invalidSerialSettings },
                            )
                        },
                        enabled = editable,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.compatibility), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.profile_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.protocol_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        UiMessages(ui)
    }
}

@Composable
private fun PresetsDialog(
    ui: FocuserUiState,
    tab: PresetTab,
    saveMode: PresetSaveMode,
    search: String,
    selectedPresetId: String?,
    presetName: String,
    presetPosition: String,
    editorError: String?,
    onDismiss: () -> Unit,
    onTabChange: (PresetTab) -> Unit,
    onSaveModeChange: (PresetSaveMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectPreset: (PositionPreset) -> Unit,
    onPresetNameChange: (String) -> Unit,
    onPresetPositionChange: (String) -> Unit,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.presets), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SegmentedButtons(
                    options = listOf(
                        PresetTab.LOAD to stringResource(R.string.preset_load),
                        PresetTab.SAVE to stringResource(R.string.preset_save),
                    ),
                    selected = tab,
                    onSelected = onTabChange,
                )
                if (tab == PresetTab.LOAD) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = onSearchChange,
                        label = { Text(stringResource(R.string.search_presets)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val filtered = ui.presets.filter { it.name.contains(search, ignoreCase = true) }
                    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        if (filtered.isEmpty()) Text(stringResource(R.string.no_matching_presets), style = MaterialTheme.typography.bodySmall)
                        filtered.forEach { preset ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelectPreset(preset) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selectedPresetId == preset.id, onClick = { onSelectPreset(preset) })
                                Column {
                                    Text(preset.name)
                                    Text(preset.position.toString(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.preset_load_note), style = MaterialTheme.typography.bodySmall)
                    DialogActions(stringResource(R.string.preset_load), selectedPresetId != null, onDismiss, onLoad)
                } else {
                    SegmentedButtons(
                        options = listOf(
                            PresetSaveMode.NEW to stringResource(R.string.preset_new),
                            PresetSaveMode.EDIT to stringResource(R.string.preset_edit),
                        ),
                        selected = saveMode,
                        onSelected = onSaveModeChange,
                    )
                    if (saveMode == PresetSaveMode.EDIT) PresetSelector(ui.presets, selectedPresetId, onSelectPreset)
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = onPresetNameChange,
                        label = { Text(stringResource(R.string.preset_name)) },
                        placeholder = { if (saveMode == PresetSaveMode.NEW) Text(stringResource(R.string.enter_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = presetPosition,
                        onValueChange = onPresetPositionChange,
                        label = { Text(stringResource(R.string.position)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContextValue(stringResource(R.string.current_position), ui.focuser.currentPosition?.toString() ?: "—", Modifier.weight(1f))
                        ContextValue(stringResource(R.string.software_limit), ui.focuser.softwareMaximum?.toString() ?: "—", Modifier.weight(1f))
                    }
                    Text(
                        if (saveMode == PresetSaveMode.NEW) stringResource(R.string.preset_new_note)
                        else stringResource(R.string.preset_edit_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    editorError?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
                    DialogActions(
                        stringResource(if (saveMode == PresetSaveMode.NEW) R.string.action_create else R.string.action_update),
                        presetName.isNotBlank() && presetPosition.isNotBlank() &&
                            (saveMode == PresetSaveMode.NEW || selectedPresetId != null),
                        onDismiss,
                        onSave,
                    )
                    if (saveMode == PresetSaveMode.EDIT && selectedPresetId != null) {
                        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(stringResource(R.string.action_delete_preset), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onImport) { Text(stringResource(R.string.action_import)) }
                    TextButton(onClick = onExport) { Text(stringResource(R.string.action_export)) }
                }
            }
        }
    }
}

@Composable
private fun PresetSelector(presets: List<PositionPreset>, selectedPresetId: String?, onSelect: (PositionPreset) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = presets.firstOrNull { it.id == selectedPresetId }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, enabled = presets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: stringResource(R.string.select_preset_to_edit))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text("${preset.name} · ${preset.position}") },
                    onClick = {
                        open = false
                        onSelect(preset)
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedButtons(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onSelected: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            if (value == selected) {
                Button(
                    onClick = { onSelected(value) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            } else {
                OutlinedButton(
                    onClick = { onSelected(value) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun DialogActions(label: String, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel_upper)) }
        Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.weight(1f)) { Text(label) }
    }
}

@Composable
private fun CompactNumberAction(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    action: String,
    onAction: () -> Unit,
    enabled: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(104.dp),
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            )
            Button(
                onClick = onAction,
                enabled = enabled,
                modifier = Modifier.width(88.dp).heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(action, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value)
    }
}

@Composable
private fun ContextValue(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun NumberSetting(label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceSetting(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled) { Text(value) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            open = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UiMessages(ui: FocuserUiState) {
    ui.message?.let { Text(localizedRuntimeMessage(it), style = MaterialTheme.typography.bodySmall) }
    ui.focuser.lastError?.let {
        Text(stringResource(R.string.serial_error, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
