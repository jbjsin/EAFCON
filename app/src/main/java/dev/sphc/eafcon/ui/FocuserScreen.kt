package dev.sphc.eafcon.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sphc.eafcon.control.MovementState
import dev.sphc.eafcon.presets.PositionPreset
import dev.sphc.eafcon.presets.PresetRules
import dev.sphc.eafcon.settings.*

private enum class AppPage(val label: String) { CONNECTION("Connection"), CONTROL("Control"), SETTINGS("Settings") }
private enum class PresetTab { LOAD, SAVE }
private enum class PresetSaveMode { NEW, EDIT }

@Composable
fun FocuserScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: FocuserViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val focuser = ui.focuser
    var pageName by rememberSaveable { mutableStateOf(AppPage.CONNECTION.name) }
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

    LaunchedEffect(focuser.softwareMaximum) {
        if (softwareMaximum.isBlank()) softwareMaximum = focuser.softwareMaximum?.toString().orEmpty()
    }
    LaunchedEffect(focuser.connected) {
        if (focuser.connected) pageName = AppPage.CONTROL.name
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
                onNavigate = { pageName = it.name },
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
                onNavigate = { pageName = it.name },
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
                    AppPage.SETTINGS -> SettingsPage(ui, viewModel)
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
            title = { Text("Delete preset?") },
            text = { Text("Delete '${preset.name}' at position ${preset.position}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePreset(preset.id)
                    deleteTarget = null
                    presetDialogOpen = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    if (!confirmReplace) ui.pendingImportPresets?.let { imported ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPresetImport,
            title = { Text("Import ${imported.size} presets?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The file is valid. Merge updates matching IDs and appends new IDs.")
                    Text("Import does not move the focuser.")
                }
            },
            confirmButton = { TextButton(onClick = viewModel::mergeImportedPresets) { Text("Merge") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmReplace = true }) { Text("Replace…") }
                    TextButton(onClick = viewModel::cancelPresetImport) { Text("Cancel") }
                }
            },
        )
    }

    if (confirmReplace && ui.pendingImportPresets != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace all presets?") },
            text = { Text("This removes all saved presets and replaces them with the imported file.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.replaceWithImportedPresets()
                    confirmReplace = false
                }) { Text("Replace all") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
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
                            text = { Text(destination.label) },
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
                    ) { Text(destination.label) }
                } else {
                    OutlinedButton(
                        onClick = { onNavigate(destination) },
                        modifier = Modifier.weight(1f),
                    ) { Text(destination.label) }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(ui: FocuserUiState, themeMode: AppThemeMode) {
    val connected = ui.focuser.connected
    val text = when {
        !connected -> "Disconnected"
        ui.demoMode -> "Connected · DEMO"
        else -> "Connected · USB"
    }
    val telemetry = buildString {
        append("P ")
        append(ui.focuser.currentPosition ?: "—")
        append(" · ")
        append(ui.focuser.temperatureCelsius?.let { "%.1f°C".format(it) } ?: "—°C")
        append(" · ")
        append(ui.focuser.movement.name)
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
                Text("USB Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (ui.devices.isEmpty()) "No compatible devices found"
                    else "${ui.devices.size} compatible device${if (ui.devices.size == 1) "" else "s"} found",
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
                                Text(device.displayInfo.driverName ?: "USB Serial", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (ui.selectedDeviceName == device.name) {
                            Column(Modifier.padding(start = 48.dp, bottom = 8.dp)) {
                                device.displayInfo.detailLines().forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Bus/device values are temporary session details.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Text("Select a device before connecting.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = viewModel::refreshDevices,
                        enabled = !ui.isWorking && !focuser.connected,
                        modifier = Modifier.weight(1f),
                    ) { Text("SCAN") }
                    Button(
                        onClick = if (focuser.connected) viewModel::disconnect else viewModel::connect,
                        enabled = if (focuser.connected) !ui.isWorking else !ui.isWorking && ui.selectedDeviceName != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (focuser.connected) "DISCONNECT" else "CONNECT") }
                }
                OutlinedButton(
                    onClick = viewModel::connectDemo,
                    enabled = !focuser.connected && !ui.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("DEMO MODE") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Connection notes", fontWeight = FontWeight.Bold)
                Text("Scanning only lists devices. A port opens after CONNECT.", style = MaterialTheme.typography.bodySmall)
                val serial = ui.connectionProfile.serial
                Text(
                    "${serial.baudRate} baud · ${serial.dataBits}${serial.parity.name.first()}${serial.stopBits.displayName} · ${serial.flowControl.displayName}",
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
                Text("CURRENT POSITION", style = MaterialTheme.typography.labelLarge)
                Text(focuser.currentPosition?.toString() ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                StatusRow("Device max", focuser.deviceMaximum?.toString() ?: "—")
                StatusRow("Software limit", focuser.softwareMaximum?.toString() ?: "Not set")
                StatusRow("Temperature", focuser.temperatureCelsius?.let { "%.1f °C".format(it) } ?: "—")
                StatusRow("Movement", focuser.movement.name)
            }
        }
        CompactNumberAction("Software limit", softwareMaximum, onSoftwareMaximumChange, "SET", onSetSoftwareMaximum,
            focuser.connected && !focuser.commandPending && softwareMaximum.isNotBlank())
        CompactNumberAction("Target position", target, onTargetChange, "GO", onMoveTo, canMove && target.isNotBlank())
        Button(
            onClick = onStop,
            enabled = focuser.connected && focuser.movement == MovementState.MOVING,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("STOP") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Relative move", fontWeight = FontWeight.Bold)
                listOf(5, 25, 50, 100).forEach { step ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onMoveBy(-step) }, enabled = canMove, modifier = Modifier.weight(1f)) { Text("−$step") }
                        Button(onClick = { onMoveBy(step) }, enabled = canMove, modifier = Modifier.weight(1f)) { Text("+$step") }
                    }
                }
                Text("Custom steps", style = MaterialTheme.typography.labelLarge)
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
        Button(onClick = onOpenPresets, modifier = Modifier.fillMaxWidth()) { Text("PRESETS") }
        UiMessages(ui)
    }
}

@Composable
private fun SettingsPage(ui: FocuserUiState, viewModel: FocuserViewModel) {
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
                Text("Serial connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(profile.name)
                Text("Settings apply to the next connection. Disconnect before editing.", style = MaterialTheme.typography.bodySmall)
            }
        }
        NumberSetting("Baud rate", baudRate, { baudRate = it.filter(Char::isDigit) }, editable)
        ChoiceSetting("Data bits", dataBits.toString(), (5..8).map { it.toString() }, editable) { dataBits = it.toInt() }
        ChoiceSetting(
            "Stop bits", SerialStopBits.valueOf(stopBitsName).displayName,
            SerialStopBits.entries.map { it.displayName }, editable,
        ) { label -> stopBitsName = SerialStopBits.entries.first { it.displayName == label }.name }
        ChoiceSetting("Parity", parityName, SerialParity.entries.map { it.name }, editable) { parityName = it }
        ChoiceSetting(
            "Flow control", SerialFlowControl.valueOf(flowControlName).displayName,
            SerialFlowControl.entries.map { it.displayName }, editable,
        ) { label -> flowControlName = SerialFlowControl.entries.first { it.displayName == label }.name }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Timeouts", fontWeight = FontWeight.Bold)
                NumberSetting("Read timeout (ms)", readTimeout, { readTimeout = it.filter(Char::isDigit) }, editable)
                NumberSetting("Write timeout (ms)", writeTimeout, { writeTimeout = it.filter(Char::isDigit) }, editable)
                NumberSetting("Response timeout (ms)", responseTimeout, { responseTimeout = it.filter(Char::isDigit) }, editable)
            }
        }
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = viewModel::resetConnectionSettings, enabled = editable, modifier = Modifier.weight(1f)) { Text("RESET") }
            Button(
                onClick = {
                    localError = runCatching {
                        SerialParameters(
                            baudRate = baudRate.toIntOrNull() ?: error("Enter a valid baud rate"),
                            dataBits = dataBits,
                            stopBits = SerialStopBits.valueOf(stopBitsName),
                            parity = SerialParity.valueOf(parityName),
                            flowControl = SerialFlowControl.valueOf(flowControlName),
                            readTimeoutMs = readTimeout.toIntOrNull() ?: error("Enter a valid read timeout"),
                            writeTimeoutMs = writeTimeout.toIntOrNull() ?: error("Enter a valid write timeout"),
                            responseTimeoutMs = responseTimeout.toIntOrNull() ?: error("Enter a valid response timeout"),
                        )
                    }.fold(
                        onSuccess = viewModel::saveConnectionSettings,
                        onFailure = { it.message ?: "Invalid serial settings" },
                    )
                },
                enabled = editable,
                modifier = Modifier.weight(1f),
            ) { Text("SAVE") }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Compatibility", fontWeight = FontWeight.Bold)
                Text(
                    "Connection values are loaded from a versioned JSON profile. Profile selection is reserved for a future update.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Changing serial values does not add a new protocol. Commands remain MyFocuserPro2-compatible and other devices are unverified.",
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
                Text("Presets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SegmentedButtons(
                    options = listOf(PresetTab.LOAD to "LOAD", PresetTab.SAVE to "SAVE"),
                    selected = tab,
                    onSelected = onTabChange,
                )
                if (tab == PresetTab.LOAD) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = onSearchChange,
                        label = { Text("Search presets") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val filtered = ui.presets.filter { it.name.contains(search, ignoreCase = true) }
                    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        if (filtered.isEmpty()) Text("No matching presets", style = MaterialTheme.typography.bodySmall)
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
                    Text("Loading only fills Target Position. It never moves the focuser.", style = MaterialTheme.typography.bodySmall)
                    DialogActions("LOAD", selectedPresetId != null, onDismiss, onLoad)
                } else {
                    SegmentedButtons(
                        options = listOf(PresetSaveMode.NEW to "NEW", PresetSaveMode.EDIT to "EDIT"),
                        selected = saveMode,
                        onSelected = onSaveModeChange,
                    )
                    if (saveMode == PresetSaveMode.EDIT) PresetSelector(ui.presets, selectedPresetId, onSelectPreset)
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = onPresetNameChange,
                        label = { Text("Preset name") },
                        placeholder = { if (saveMode == PresetSaveMode.NEW) Text("Enter a name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = presetPosition,
                        onValueChange = onPresetPositionChange,
                        label = { Text("Position") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContextValue("Current position", ui.focuser.currentPosition?.toString() ?: "—", Modifier.weight(1f))
                        ContextValue("Software limit", ui.focuser.softwareMaximum?.toString() ?: "—", Modifier.weight(1f))
                    }
                    Text(
                        if (saveMode == PresetSaveMode.NEW) "Position starts at the current value. You can edit it before creating."
                        else "UPDATE changes the selected preset. It never moves the focuser.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    editorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    DialogActions(
                        if (saveMode == PresetSaveMode.NEW) "CREATE" else "UPDATE",
                        presetName.isNotBlank() && presetPosition.isNotBlank() &&
                            (saveMode == PresetSaveMode.NEW || selectedPresetId != null),
                        onDismiss,
                        onSave,
                    )
                    if (saveMode == PresetSaveMode.EDIT && selectedPresetId != null) {
                        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("DELETE PRESET", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onImport) { Text("Import") }
                    TextButton(onClick = onExport) { Text("Export") }
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
            Text(selected?.name ?: "Select a preset to edit")
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
private fun <T> SegmentedButtons(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            if (value == selected) Button(onClick = { onSelected(value) }, modifier = Modifier.weight(1f)) { Text(label) }
            else OutlinedButton(onClick = { onSelected(value) }, modifier = Modifier.weight(1f)) { Text(label) }
        }
    }
}

@Composable
private fun DialogActions(label: String, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("CANCEL") }
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
    ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    ui.focuser.lastError?.let {
        Text("Serial error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
