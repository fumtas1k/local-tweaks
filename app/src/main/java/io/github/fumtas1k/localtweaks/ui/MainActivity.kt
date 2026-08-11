package io.github.fumtas1k.localtweaks.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.fumtas1k.localtweaks.R
import io.github.fumtas1k.localtweaks.adb.AdbInputValidator
import io.github.fumtas1k.localtweaks.adb.LocalAdbSession
import io.github.fumtas1k.localtweaks.feature.shutter.ForcedSettingValue
import io.github.fumtas1k.localtweaks.ui.theme.LocalTweaksTheme

class MainActivity : ComponentActivity() {
    private lateinit var session: LocalAdbSession
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = LocalAdbSession.getInstance(applicationContext)
        viewModel = ViewModelProvider(this, MainViewModel.factory(session))[MainViewModel::class.java]
        setContent {
            LocalTweaksTheme {
                LocalTweaksScreen(viewModel, ::openWirelessDebuggingSettings)
            }
        }
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
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (screen != MainScreen.Home && !restartRequired) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                }
            }
        },
    )
}

/**
 * A single labelled section of a screen: a `titleMedium` heading followed by
 * body content and, optionally, an action - all inside a [Card] on
 * [MaterialTheme.colorScheme.surfaceContainerHigh] unless overridden. This
 * sits above the screen background and above disabled cards (see
 * [CameraFeatureCard]) in both light and dark themes.
 */
@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Right-aligns a single primary or secondary action button within a [SectionCard]. */
@Composable
private fun TrailingAction(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        content()
    }
}

/**
 * A button label that reserves space for a progress indicator so the button never resizes.
 * The same size is reserved on both sides of the text so the label stays centered whether or
 * not the indicator is showing.
 */
@Composable
private fun ButtonLabel(text: String, showProgress: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }
        Text(text)
        Spacer(modifier = Modifier.size(16.dp))
    }
}

/** Renders a status line following the tone rules: icon (if any), color, and text. */
@Composable
private fun StatusIndicator(tone: MainStatusTone, text: String, modifier: Modifier = Modifier) {
    val color = when (tone) {
        MainStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        MainStatusTone.Progress -> LocalContentColor.current
        MainStatusTone.Success -> MaterialTheme.colorScheme.primary
        MainStatusTone.Failure -> MaterialTheme.colorScheme.error
        MainStatusTone.Warning -> MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (tone) {
            MainStatusTone.Progress -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            MainStatusTone.Success -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color)
            MainStatusTone.Failure -> Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = color,
            )
            MainStatusTone.Warning -> Icon(Icons.Filled.Warning, contentDescription = null, tint = color)
            MainStatusTone.Neutral -> Unit
        }
        Text(text, color = color)
    }
}

/**
 * The whole-screen connection status line at the top of the connection settings screen.
 * While [status] is a restart warning, it is pinned in an `errorContainer` card.
 */
@Composable
private fun ConnectionStatusHeader(status: MainStatus, modifier: Modifier = Modifier) {
    val tone = mainStatusTone(status)
    val text = stringResource(R.string.status, statusText(status))
    if (tone == MainStatusTone.Warning) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            StatusIndicator(tone = tone, text = text, modifier = Modifier.padding(16.dp))
        }
    } else {
        StatusIndicator(tone = tone, text = text, modifier = modifier.fillMaxWidth())
    }
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
    // This area shows connection state only. state.status also carries the
    // result of unrelated operations (read/write/pairing/credential reset)
    // on other screens, which must never leak into this card.
    val tone = when {
        state.restartRequired -> MainStatusTone.Warning
        state.connected -> MainStatusTone.Success
        else -> MainStatusTone.Neutral
    }
    val connectionText = when {
        state.restartRequired -> stringResource(R.string.restart_required)
        state.connected -> stringResource(R.string.connection_success)
        else -> stringResource(R.string.status_not_connected)
    }

    SectionCard {
        Text(stringResource(R.string.adb_connection), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusIndicator(
                tone = tone,
                text = stringResource(R.string.status, connectionText),
                modifier = Modifier.weight(1f),
            )
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
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

    ConnectionStatusHeader(status = state.status)

    SectionCard {
        Text(stringResource(R.string.wireless_debugging), style = MaterialTheme.typography.titleMedium)
        FilledTonalButton(onClick = onOpenSettings, enabled = actionsEnabled) {
            Text(stringResource(R.string.open_developer_settings))
        }
    }

    SectionCard {
        Text(stringResource(R.string.connection_heading), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.connectionPort,
            onValueChange = onConnectionPortChange,
            label = { Text(stringResource(R.string.connection_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = state.status == MainStatus.InvalidConnectionPort,
            supportingText = if (state.status == MainStatus.InvalidConnectionPort) {
                { Text(stringResource(R.string.invalid_connection_port)) }
            } else {
                null
            },
            enabled = actionsEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        TrailingAction {
            Button(onClick = onConnect, enabled = actionsEnabled) {
                ButtonLabel(
                    text = stringResource(R.string.connect),
                    showProgress = state.busy && state.status == MainStatus.Connecting,
                )
            }
        }
    }

    SectionCard {
        Text(stringResource(R.string.pairing_heading), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.pairingPort,
            onValueChange = onPairingPortChange,
            label = { Text(stringResource(R.string.pairing_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = state.status == MainStatus.InvalidPairingPort,
            supportingText = if (state.status == MainStatus.InvalidPairingPort) {
                { Text(stringResource(R.string.invalid_pairing_port)) }
            } else {
                null
            },
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
            isError = state.status == MainStatus.InvalidPairingCode,
            supportingText = if (state.status == MainStatus.InvalidPairingCode) {
                { Text(stringResource(R.string.invalid_pairing_code)) }
            } else {
                null
            },
            enabled = actionsEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        TrailingAction {
            FilledTonalButton(onClick = onPair, enabled = actionsEnabled) {
                ButtonLabel(
                    text = stringResource(R.string.pair),
                    showProgress = state.busy && state.status == MainStatus.Pairing,
                )
            }
        }
    }

    SectionCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.error,
    ) {
        Text(stringResource(R.string.danger_zone), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.reset_credentials_description))
        TrailingAction {
            OutlinedButton(
                onClick = onResetCredentials,
                enabled = actionsEnabled,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (actionsEnabled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                    },
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                ),
            ) {
                ButtonLabel(
                    text = stringResource(R.string.reset_credentials),
                    showProgress = state.busy && state.status == MainStatus.CredentialResetting,
                )
            }
        }
    }
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
    val isWritingSetting = state.status == MainStatus.SettingZero || state.status == MainStatus.SettingOne

    SectionCard {
        Text(stringResource(R.string.current_value_heading), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.current_value, valueText), style = MaterialTheme.typography.headlineSmall)
        if (state.currentValue == null && state.status == MainStatus.Connected) {
            Text(
                stringResource(R.string.read_current_setting_prompt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StatusIndicator(
                tone = mainStatusTone(state.status),
                text = stringResource(R.string.operation_result, statusText(state.status)),
            )
        }
        TrailingAction {
            Button(onClick = onRead, enabled = actionsEnabled) {
                ButtonLabel(
                    text = stringResource(
                        if (state.currentValue == null) R.string.read_current_setting else R.string.refresh_current_setting,
                    ),
                    showProgress = state.busy && state.status == MainStatus.Reading,
                )
            }
        }
    }

    SectionCard {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isWritingSetting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Switch(
                    checked = switchState == ForcedShutterSwitchState.On,
                    onCheckedChange = { checked -> if (checked) onSetOne() else onSetZero() },
                    enabled = switchEnabled,
                    modifier = Modifier.semantics { contentDescription = switchDescription },
                )
            }
        }
    }

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

@Composable
private fun PreviewContainer(content: @Composable ColumnScope.() -> Unit) {
    LocalTweaksTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Preview(name = "Home - Light", showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    PreviewContainer {
        HomeScreen(state = MainUiState(), onOpenConnectionSettings = {}, onOpenCameraSettings = {})
    }
}

@Preview(name = "Home - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    PreviewContainer {
        HomeScreen(
            state = MainUiState(connected = true, status = MainStatus.Connected),
            onOpenConnectionSettings = {},
            onOpenCameraSettings = {},
        )
    }
}

@Preview(name = "Connection Settings - Light", showBackground = true)
@Composable
private fun ConnectionSettingsScreenLightPreview() {
    PreviewContainer {
        ConnectionSettingsScreen(
            state = MainUiState(),
            pairingCode = "",
            onPairingPortChange = {},
            onConnectionPortChange = {},
            onPairingCodeChange = {},
            onPair = {},
            onConnect = {},
            onOpenSettings = {},
            onResetCredentials = {},
        )
    }
}

@Preview(name = "Connection Settings - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectionSettingsScreenDarkPreview() {
    PreviewContainer {
        ConnectionSettingsScreen(
            state = MainUiState(restartRequired = true, status = MainStatus.RestartRequired),
            pairingCode = "",
            onPairingPortChange = {},
            onConnectionPortChange = {},
            onPairingCodeChange = {},
            onPair = {},
            onConnect = {},
            onOpenSettings = {},
            onResetCredentials = {},
        )
    }
}

@Preview(name = "Camera Settings - Light", showBackground = true)
@Composable
private fun CameraSettingsScreenLightPreview() {
    PreviewContainer {
        CameraSettingsScreen(
            state = MainUiState(
                connected = true,
                currentValue = ForcedSettingValue.Present("0"),
                status = MainStatus.ReadComplete,
            ),
            onRead = {},
            onSetZero = {},
            onSetOne = {},
        )
    }
}

@Preview(name = "Camera Settings - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CameraSettingsScreenDarkPreview() {
    PreviewContainer {
        CameraSettingsScreen(
            state = MainUiState(
                connected = true,
                currentValue = ForcedSettingValue.Present("1"),
                status = MainStatus.SetOneSuccess,
            ),
            onRead = {},
            onSetZero = {},
            onSetOne = {},
        )
    }
}
