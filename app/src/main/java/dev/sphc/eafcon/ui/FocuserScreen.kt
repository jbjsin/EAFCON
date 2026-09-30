package dev.sphc.eafcon.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sphc.eafcon.control.MovementState
import dev.sphc.eafcon.presets.PositionPreset
import dev.sphc.eafcon.presets.PresetRules

@Composable
fun FocuserScreen(viewModel: FocuserViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf("") }
    var softwareMaximum by remember { mutableStateOf("") }
    var customStep by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var presetName by remember { mutableStateOf("") }
    var presetPosition by remember { mutableStateOf("") }
    var presetEditorError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<PositionPreset?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    val focuser = ui.focuser
    val canMove = focuser.connected && focuser.movement == MovementState.IDLE &&
        !focuser.commandPending && focuser.currentPosition != null && focuser.softwareMaximum != null
    LaunchedEffect(ui.pendingImportPresets) {
        if (ui.pendingImportPresets == null) confirmReplace = false
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportPresets(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPresets(uri)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gemini Focuser", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("USB Serial", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !focuser.connected -> "Disconnected"
                        ui.demoMode -> "Connected · DEMO (simulated)"
                        else -> "Connected · USB"
                    },
                )
                if (ui.devices.isEmpty()) Text("No USB serial devices found")
                ui.devices.forEach { device ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !focuser.connected) {
                                viewModel.selectDevice(device.name)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = ui.selectedDeviceName == device.name,
                                onClick = { viewModel.selectDevice(device.name) },
                                enabled = !focuser.connected,
                            )
                            Text(device.stableLabel, style = MaterialTheme.typography.bodySmall)
                        }
                        if (ui.selectedDeviceName == device.name) {
                            Column(Modifier.padding(start = 48.dp, bottom = 8.dp)) {
                                device.displayInfo.detailLines().forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "Bus/device values identify this current USB session only.",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::refreshDevices, enabled = !ui.isWorking) { Text("Scan") }
                    Button(
                        onClick = if (focuser.connected) viewModel::disconnect else viewModel::connect,
                        enabled = if (focuser.connected) !ui.isWorking else !ui.isWorking && ui.selectedDeviceName != null,
                    ) { Text(if (ui.isWorking && !focuser.connected) "Connecting…" else if (focuser.connected) "Disconnect" else "Connect") }
                    Button(onClick = viewModel::connectDemo, enabled = !focuser.connected && !ui.isWorking) {
                        Text("Demo")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Current Position", style = MaterialTheme.typography.titleMedium)
                Text(focuser.currentPosition?.toString() ?: "—", style = MaterialTheme.typography.displaySmall)
                HorizontalDivider()
                Text("Device maximum: ${focuser.deviceMaximum ?: "—"}")
                Text("Software safety maximum: ${focuser.softwareMaximum ?: "not set"}")
                Text("Movement: ${focuser.movement.name}    Temperature: ${focuser.temperatureCelsius?.let { "%.2f °C".format(it) } ?: "—"}")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Position Presets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        editingId = null
                        presetName = ""
                        presetPosition = focuser.currentPosition?.toString().orEmpty()
                        presetEditorError = null
                        editorOpen = true
                    }) { Text("Add") }
                }
                if (ui.presets.isEmpty()) Text("No saved presets")
                ui.presets.forEach { preset ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).clickable {
                                    if (viewModel.selectPreset(preset)) target = preset.position.toString()
                                }.padding(vertical = 4.dp),
                            ) {
                                Text(preset.name, fontWeight = FontWeight.Medium)
                                Text("Position ${preset.position} · tap to load target", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = {
                                editingId = preset.id
                                presetName = preset.name
                                presetPosition = preset.position.toString()
                                presetEditorError = null
                                editorOpen = true
                            }) { Text("Edit") }
                            TextButton(onClick = { deleteTarget = preset }) { Text("Delete") }
                        }
                        HorizontalDivider()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("EAFCon_Presets.json") }) { Text("Export JSON") }
                    OutlinedButton(onClick = {
                        confirmReplace = false
                        viewModel.cancelPresetImport()
                        importLauncher.launch(arrayOf("application/json", "text/json", "*/*"))
                    }) {
                        Text("Import JSON")
                    }
                }
                Text("Selecting a preset fills Target Position; it never moves the focuser by itself.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = softwareMaximum,
                onValueChange = { softwareMaximum = it.filter(Char::isDigit) },
                label = { Text("Software safety maximum") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.setSoftwareMaximum(softwareMaximum) }, enabled = focuser.connected) {
                Text("Set")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter(Char::isDigit) },
                label = { Text("Target position") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.moveTo(target) }, enabled = canMove) { Text("GO") }
        }
        Button(
            onClick = viewModel::stop,
            enabled = focuser.connected && focuser.movement == MovementState.MOVING,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("STOP") }
        Text("Relative move", style = MaterialTheme.typography.titleMedium)
        listOf(5, 25, 50, 100).forEach { step ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.moveBy(-step) }, enabled = canMove, modifier = Modifier.weight(1f)) {
                    Text("−$step")
                }
                Button(onClick = { viewModel.moveBy(step) }, enabled = canMove, modifier = Modifier.weight(1f)) {
                    Text("+$step")
                }
            }
        }
        OutlinedTextField(
            value = customStep,
            onValueChange = { customStep = it.filter(Char::isDigit) },
            label = { Text("Custom steps") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val customValue = customStep.toIntOrNull()?.takeIf { it > 0 }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { customValue?.let { viewModel.moveBy(-it) } },
                enabled = canMove && customValue != null,
                modifier = Modifier.weight(1f),
            ) { Text("− Custom") }
            Button(
                onClick = { customValue?.let { viewModel.moveBy(it) } },
                enabled = canMove && customValue != null,
                modifier = Modifier.weight(1f),
            ) { Text("+ Custom") }
        }
        ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        focuser.lastError?.let { Text("Serial error: $it", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(4.dp))
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text(if (editingId == null) "Add preset" else "Edit preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { if (it.length <= PresetRules.MAX_NAME_LENGTH) presetName = it },
                        label = { Text("Preset name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = presetPosition,
                        onValueChange = { presetPosition = it.filter(Char::isDigit) },
                        label = { Text("Position") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    TextButton(
                        onClick = { focuser.currentPosition?.let { presetPosition = it.toString() } },
                        enabled = focuser.currentPosition != null,
                    ) { Text("Use current position (${focuser.currentPosition ?: "—"})") }
                    presetEditorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val error = viewModel.savePreset(editingId, presetName, presetPosition)
                    if (error == null) editorOpen = false else presetEditorError = error
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete preset?") },
            text = { Text("Delete '${preset.name}' at position ${preset.position}?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePreset(preset.id); deleteTarget = null }) { Text("Delete") }
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
                    Text("The file is valid. Merge updates matching IDs and appends new IDs; duplicate names with different IDs remain separate.")
                    Text("Import does not move the focuser.")
                    ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::mergeImportedPresets) { Text("Merge") }
            },
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
            text = { Text("This will remove all currently saved presets and replace them with the imported file.") },
            confirmButton = {
                TextButton(onClick = { viewModel.replaceWithImportedPresets(); confirmReplace = false }) { Text("Replace all") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}
