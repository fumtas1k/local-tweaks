package io.github.fumtas1k.localtweaks.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.fumtas1k.localtweaks.R
import io.github.fumtas1k.localtweaks.adb.LocalAdbSession
import io.github.fumtas1k.localtweaks.feature.shutter.ForcedSettingValue

class MainActivity : ComponentActivity() {
    private lateinit var session: LocalAdbSession
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = LocalAdbSession.getInstance(applicationContext)
        viewModel = ViewModelProvider(this, MainViewModel.factory(session))[MainViewModel::class.java]
        setContent { LocalTweaksScreen(viewModel, ::openWirelessDebuggingSettings) }
    }

    private fun openWirelessDebuggingSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }
}

@Composable
private fun LocalTweaksScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var pairingCode by remember { mutableStateOf("") }
    val valueText = when (val value = state.currentValue) {
        null -> stringResource(R.string.value_not_read)
        ForcedSettingValue.NotSet -> stringResource(R.string.value_not_set)
        is ForcedSettingValue.Present -> value.raw
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.local_adb), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.status, statusText(state.status)))
                Text(stringResource(R.string.current_value, valueText))

                Button(
                    onClick = onOpenSettings,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.open_wireless_debugging)) }

                OutlinedTextField(
                    value = state.pairingPort,
                    onValueChange = viewModel::setPairingPort,
                    label = { Text(stringResource(R.string.pairing_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text(stringResource(R.string.pairing_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val code = pairingCode
                        pairingCode = ""
                        viewModel.pair(code)
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.pair)) }

                OutlinedTextField(
                    value = state.connectionPort,
                    onValueChange = viewModel::setConnectionPort,
                    label = { Text(stringResource(R.string.connection_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::connect,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.connect)) }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = viewModel::read,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.read_current_setting)) }
            }
        }
    }
}

@Composable
private fun statusText(status: MainStatus): String = when (status) {
    MainStatus.NotConnected -> stringResource(R.string.status_not_connected)
    MainStatus.InvalidPairingPort -> stringResource(R.string.invalid_pairing_port)
    MainStatus.InvalidPairingCode -> stringResource(R.string.invalid_pairing_code)
    MainStatus.Pairing -> stringResource(R.string.pairing_progress)
    MainStatus.Paired -> stringResource(R.string.pairing_success)
    MainStatus.PairingFailed -> stringResource(R.string.pairing_failed)
    MainStatus.InvalidConnectionPort -> stringResource(R.string.invalid_connection_port)
    MainStatus.Connecting -> stringResource(R.string.connecting_progress)
    MainStatus.Connected -> stringResource(R.string.connection_success)
    MainStatus.ConnectionFailed -> stringResource(R.string.connection_failed)
    MainStatus.Reading -> stringResource(R.string.reading_progress)
    MainStatus.ReadComplete -> stringResource(R.string.read_complete)
    MainStatus.InvalidOutput -> stringResource(R.string.invalid_output)
    MainStatus.ReadTransportFailed -> stringResource(R.string.read_transport_failed)
}
