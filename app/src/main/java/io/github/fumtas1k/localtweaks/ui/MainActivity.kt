package io.github.fumtas1k.localtweaks.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.fumtas1k.localtweaks.R
import io.github.fumtas1k.localtweaks.adb.AdbInputValidator
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
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.screen) {
        if (state.screen != MainScreen.ConnectionSettings) pairingCode = ""
    }

    BackHandler(enabled = state.screen != MainScreen.Home && !state.restartRequired) {
        viewModel.openHome()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    LocalTweaksTopAppBar(
                        screen = state.screen,
                        restartRequired = state.restartRequired,
                        onBack = viewModel::openHome,
                    )
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (state.screen) {
                        MainScreen.Home -> HomeScreen(
                            state = state,
                            onOpenConnectionSettings = viewModel::openConnectionSettings,
                            onOpenCameraSettings = viewModel::openCameraSettings,
                        )
                        MainScreen.ConnectionSettings -> ConnectionSettingsScreen(
                            state = state,
                            pairingCode = pairingCode,
                            onPairingPortChange = viewModel::setPairingPort,
                            onConnectionPortChange = viewModel::setConnectionPort,
                            onPairingCodeChange = { value ->
                                AdbInputValidator.acceptBoundedAsciiDigits(value, 6)?.let { pairingCode = it }
                            },
                            onPair = {
                                val code = pairingCode
                                pairingCode = ""
                                viewModel.pair(code)
                            },
                            onConnect = viewModel::connect,
                            onOpenSettings = onOpenSettings,
                            onResetCredentials = { showResetDialog = true },
                        )
                        MainScreen.CameraSettings -> CameraSettingsScreen(
                            state = state,
                            onRead = viewModel::read,
                            onSetZero = viewModel::setZero,
                            onSetOne = viewModel::setOne,
                        )
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_credentials_title)) },
            text = { Text(stringResource(R.string.reset_credentials_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetCredentials()
                    },
                ) { Text(stringResource(R.string.reset_credentials_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LocalTweaksTopAppBar(
    screen: MainScreen,
    restartRequired: Boolean,
    onBack: () -> Unit,
) {
    val title = when (screen) {
        MainScreen.Home -> stringResource(R.string.app_name)
        MainScreen.ConnectionSettings -> stringResource(R.string.connection_settings)
        MainScreen.CameraSettings -> stringResource(R.string.camera_settings)
    }
    val backDescription = stringResource(R.string.navigate_back)
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (screen != MainScreen.Home && !restartRequired) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics {
                        contentDescription = backDescription
                    },
                ) { Text("←", style = MaterialTheme.typography.titleLarge) }
            }
        },
    )
}

@Composable
private fun HomeScreen(
    state: MainUiState,
    onOpenConnectionSettings: () -> Unit,
    onOpenCameraSettings: () -> Unit,
) {
    ConnectionStatusArea(
        state = state,
        onOpenConnectionSettings = onOpenConnectionSettings,
    )
    Text(stringResource(R.string.features), style = MaterialTheme.typography.titleMedium)
    FeatureList(
        connected = state.connected && !state.restartRequired,
        onOpenCameraSettings = onOpenCameraSettings,
    )
}

@Composable
private fun ConnectionStatusArea(state: MainUiState, onOpenConnectionSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.adb_connection), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val connectionStatus = stringResource(
                if (state.connected) R.string.connection_success else R.string.status_not_connected,
            )
            Text(stringResource(R.string.status, connectionStatus))
            TextButton(onClick = onOpenConnectionSettings) {
                Text(
                    stringResource(
                        if (state.connected) R.string.open_connection_settings else R.string.connect_to_device,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FeatureList(connected: Boolean, onOpenCameraSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CameraFeatureCard(connected = connected, onOpenCameraSettings = onOpenCameraSettings)
    }
}

@Composable
private fun CameraFeatureCard(connected: Boolean, onOpenCameraSettings: () -> Unit) {
    Card(
        onClick = onOpenCameraSettings,
        enabled = connected,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.camera_settings), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (connected) R.string.camera_feature_available else R.string.camera_feature_requires_connection,
                    ),
                )
            }
            if (connected) {
                Spacer(Modifier.width(12.dp))
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun ConnectionSettingsScreen(
    state: MainUiState,
    pairingCode: String,
    onPairingPortChange: (String) -> Unit,
    onConnectionPortChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onResetCredentials: () -> Unit,
) {
    val actionsEnabled = !state.busy && !state.restartRequired

    Text(stringResource(R.string.status, statusText(state.status)))
    HorizontalDivider()
    Text(stringResource(R.string.wireless_debugging), style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = onOpenSettings) {
        Text(stringResource(R.string.open_developer_settings))
    }

    HorizontalDivider()
    Text(stringResource(R.string.connection_heading), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.connectionPort,
        onValueChange = onConnectionPortChange,
        label = { Text(stringResource(R.string.connection_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = actionsEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onConnect, enabled = actionsEnabled) {
        Text(stringResource(R.string.connect))
    }

    HorizontalDivider()
    Text(stringResource(R.string.pairing_heading), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.pairingPort,
        onValueChange = onPairingPortChange,
        label = { Text(stringResource(R.string.pairing_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = actionsEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = pairingCode,
        onValueChange = onPairingCodeChange,
        label = { Text(stringResource(R.string.pairing_code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        enabled = actionsEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(onClick = onPair, enabled = actionsEnabled) {
        Text(stringResource(R.string.pair))
    }

    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.danger_zone),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Text(stringResource(R.string.reset_credentials_description))
    TextButton(
        onClick = onResetCredentials,
        enabled = actionsEnabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(stringResource(R.string.reset_credentials)) }
}

@Composable
private fun CameraSettingsScreen(
    state: MainUiState,
    onRead: () -> Unit,
    onSetZero: () -> Unit,
    onSetOne: () -> Unit,
) {
    val actionsEnabled = !state.busy && !state.restartRequired
    val valueText = when (val value = state.currentValue) {
        null -> stringResource(R.string.value_not_read)
        ForcedSettingValue.NotSet -> stringResource(R.string.value_not_set)
        is ForcedSettingValue.Present -> value.raw
    }
    val resultText = if (state.currentValue == null && state.status == MainStatus.Connected) {
        stringResource(R.string.read_current_setting_prompt)
    } else {
        statusText(state.status)
    }
    val switchState = forcedShutterSwitchState(state.currentValue)
    val switchEnabled = actionsEnabled && switchState != ForcedShutterSwitchState.Disabled
    val switchDescription = stringResource(R.string.forced_shutter_setting)
    val switchUnavailableReason = when (state.currentValue) {
        null -> stringResource(R.string.switch_requires_current_value)
        ForcedSettingValue.NotSet -> stringResource(R.string.switch_unavailable_not_set)
        is ForcedSettingValue.Present -> if (switchState == ForcedShutterSwitchState.Disabled) {
            stringResource(R.string.switch_unavailable_unknown_value)
        } else {
            null
        }
    }

    Text(stringResource(R.string.current_value_heading), style = MaterialTheme.typography.titleMedium)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.current_value, valueText), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.operation_result, resultText))
        }
    }

    Button(onClick = onRead, enabled = actionsEnabled) {
        Text(
            stringResource(
                if (state.currentValue == null) R.string.read_current_setting else R.string.refresh_current_setting,
            ),
        )
    }

    HorizontalDivider()
    Text(stringResource(R.string.change_setting), style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.forced_shutter_setting))
            Text(stringResource(R.string.forced_shutter_setting_mapping))
            switchUnavailableReason?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = switchState == ForcedShutterSwitchState.On,
            onCheckedChange = { checked -> if (checked) onSetOne() else onSetZero() },
            enabled = switchEnabled,
            modifier = Modifier.semantics { contentDescription = switchDescription },
        )
    }

    HorizontalDivider()
    Text(stringResource(R.string.notice_heading), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.camera_settings_notice),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    MainStatus.ReadTimeout -> stringResource(R.string.read_timeout)
    MainStatus.ReadTransportFailed -> stringResource(R.string.read_transport_failed)
    MainStatus.SettingZero -> stringResource(R.string.set_zero_progress)
    MainStatus.SettingOne -> stringResource(R.string.set_one_progress)
    MainStatus.SetZeroSuccess -> stringResource(R.string.set_zero_success)
    MainStatus.SetOneSuccess -> stringResource(R.string.set_one_success)
    MainStatus.WriteReadBackMismatch -> stringResource(R.string.write_read_back_mismatch)
    MainStatus.WriteInvalidOutput -> stringResource(R.string.write_invalid_output)
    MainStatus.WriteTimeout -> stringResource(R.string.write_timeout)
    MainStatus.WriteTransportFailed -> stringResource(R.string.write_transport_failed)
    MainStatus.CredentialResetting -> stringResource(R.string.reset_credentials_progress)
    MainStatus.RestartRequired -> stringResource(R.string.restart_required)
    MainStatus.CredentialResetFailed -> stringResource(R.string.reset_credentials_failed)
}
