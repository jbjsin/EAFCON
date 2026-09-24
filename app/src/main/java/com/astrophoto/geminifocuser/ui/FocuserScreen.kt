package com.astrophoto.geminifocuser.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.astrophoto.geminifocuser.control.MovementState

@Composable
fun FocuserScreen(viewModel: FocuserViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf("") }
    var softwareMaximum by remember { mutableStateOf("") }
    var customStep by remember { mutableStateOf("") }
    val focuser = ui.focuser
    val canMove = focuser.connected && focuser.movement == MovementState.IDLE &&
        !focuser.commandPending && focuser.currentPosition != null && focuser.softwareMaximum != null

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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::refreshDevices, enabled = !ui.isWorking) { Text("Rescan") }
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
}
