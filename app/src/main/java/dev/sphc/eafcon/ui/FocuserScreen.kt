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
import dev.sphc.eafcon.presets.PositionPreset
import dev.sphc.eafcon.presets.PresetRules
import dev.sphc.eafcon.settings.*

internal enum class AppPage { CONNECTION, CONTROL, SETTINGS }
private enum class PresetTab { LOAD, SAVE }
private enum class PresetSaveMode { NEW, EDIT }

internal val AppPage.isPrimary: Boolean
    get() = this == AppPage.CONNECTION || this == AppPage.CONTROL

internal fun secondaryBackDestination(current: AppPage, lastPrimary: AppPage): AppPage? = when {
    current.isPrimary -> null
    lastPrimary.isPrimary -> lastPrimary
    else -> AppPage.CONNECTION
}

@Composable
private fun AppPage.localizedLabel(): String = stringResource(
    when (this) {
        AppPage.CONNECTION -> R.string.page_connection
        AppPage.CONTROL -> R.string.page_control
        AppPage.SETTINGS -> R.string.page_settings
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
    Regex("^Validated (\\d+) presets\\. Choose how to apply them\\.$").matchEntire(raw)?.let {
        return stringResource(R.string.message_validated_presets, it.groupValues[1].toInt())
    }
    Regex("^Merged (\\d+) presets$").matchEntire(raw)?.let {
        return stringResource(R.string.message_merged_presets, it.groupValues[1].toInt())
    }
    Regex("^Replaced presets with (\\d+) imported presets$").matchEntire(raw)?.let {
        return stringResource(R.string.message_replaced_presets, it.groupValues[1].toInt())
    }
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
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(focuser.connected) {
        if (focuser.connected) {
            pageName = AppPage.CONTROL.name
            lastPrimaryPageName = AppPage.CONTROL.name
        }
    }
    LaunchedEffect(ui.pendingImportPresets) {
        if (ui.pendingImportPresets == null) confirmReplace = false
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
                confirmReplace = false
                viewModel.cancelPresetImport()
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

    if (!confirmReplace) ui.pendingImportPresets?.let { imported ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPresetImport,
            title = { Text(stringResource(R.string.import_presets_title, imported.size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.import_merge_note))
                    Text(stringResource(R.string.import_no_move_note))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::mergeImportedPresets) { Text(stringResource(R.string.action_merge)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmReplace = true }) { Text(stringResource(R.string.action_replace_ellipsis)) }
                    TextButton(onClick = viewModel::cancelPresetImport) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }

    if (confirmReplace && ui.pendingImportPresets != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(stringResource(R.string.replace_presets_title)) },
            text = { Text(stringResource(R.string.replace_presets_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.replaceWithImportedPresets()
                    confirmReplace = false
                }) { Text(stringResource(R.string.action_replace_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EafconTopBar(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onNavigate: (AppPage) -> Unit,
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
                    AppPage.entries.forEach { destination ->
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
        append(ui.focuser.temperatureCelsius?.let { "%.1f°C".format(it) } ?: "—°C")
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
                StatusRow(stringResource(R.string.temperature), focuser.temperatureCelsius?.let { "%.1f °C".format(it) } ?: "—")
                StatusRow(stringResource(R.string.movement), focuser.movement.localizedLabel())
            }
        }
        CompactNumberAction(stringResource(R.string.software_limit), softwareMaximum, onSoftwareMaximumChange, stringResource(R.string.action_set), onSetSoftwareMaximum,
            focuser.connected && !focuser.commandPending && softwareMaximum.isNotBlank())
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
