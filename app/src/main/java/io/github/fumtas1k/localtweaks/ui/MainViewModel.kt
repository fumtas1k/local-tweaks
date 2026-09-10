package io.github.fumtas1k.localtweaks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.fumtas1k.localtweaks.R
import io.github.fumtas1k.localtweaks.adb.AdbInputValidator
import io.github.fumtas1k.localtweaks.adb.AdbRestartRequiredException
import io.github.fumtas1k.localtweaks.adb.ConnectFailure
import io.github.fumtas1k.localtweaks.adb.ConnectionPreferences
import io.github.fumtas1k.localtweaks.adb.LocalAdbSession
import io.github.fumtas1k.localtweaks.adb.classifyConnectFailure
import io.github.fumtas1k.localtweaks.adb.restoreValidatedConnectionPort
import io.github.fumtas1k.localtweaks.adb.saveConnectionPortIfConnected
import io.github.fumtas1k.localtweaks.feature.shutter.ForcedSettingValue
import io.github.fumtas1k.localtweaks.feature.shutter.ShutterReadError
import io.github.fumtas1k.localtweaks.feature.shutter.ShutterReadResult
import io.github.fumtas1k.localtweaks.feature.shutter.ShutterRepository
import io.github.fumtas1k.localtweaks.feature.shutter.ShutterWriteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class MainStatus {
    NotConnected,
    InvalidPairingPort,
    InvalidPairingCode,
    Pairing,
    Paired,
    PairingFailed,
    InvalidConnectionPort,
    Connecting,
    Connected,
    ConnectionFailed,
    ConnectionPairingRequired,
    ConnectionPortUnavailable,
    Reading,
    ReadComplete,
    InvalidOutput,
    ReadTimeout,
    ReadTransportFailed,
    SettingZero,
    SettingOne,
    SetZeroSuccess,
    SetOneSuccess,
    WriteReadBackMismatch,
    WriteInvalidOutput,
    WriteTimeout,
    WriteTransportFailed,
    CredentialResetting,
    RestartRequired,
    CredentialResetFailed,
}

internal enum class MainScreen {
    Home,
    ConnectionSettings,
    CameraSettings,
}

internal data class MainUiState(
    val pairingPort: String = "",
    val connectionPort: String = "",
    val status: MainStatus = MainStatus.NotConnected,
    val currentValue: ForcedSettingValue? = null,
    val busy: Boolean = false,
    val restartRequired: Boolean = false,
    val connected: Boolean = false,
    val screen: MainScreen = MainScreen.Home,
)

internal fun MainUiState.openHome(): MainUiState =
    if (restartRequired) copy(screen = MainScreen.ConnectionSettings) else copy(screen = MainScreen.Home)

internal fun MainUiState.openConnectionSettings(): MainUiState =
    copy(
        screen = MainScreen.ConnectionSettings,
        status = when {
            restartRequired -> MainStatus.RestartRequired
            connected -> MainStatus.Connected
            else -> MainStatus.NotConnected
        },
    )

internal fun MainUiState.openCameraSettings(): MainUiState =
    if (connected && !restartRequired) copy(screen = MainScreen.CameraSettings) else this

internal fun MainUiState.markConnected(): MainUiState = copy(
    status = MainStatus.Connected,
    currentValue = null,
    busy = false,
    connected = true,
).openHome()

internal fun MainUiState.markRestartRequired(): MainUiState = copy(
    restartRequired = true,
    connected = false,
    screen = MainScreen.ConnectionSettings,
)

/** Applies the outcome of a failed [LocalAdbSession.connect] call, classified by [ConnectFailure]. */
internal fun MainUiState.applyConnectFailure(failure: ConnectFailure): MainUiState = when (failure) {
    ConnectFailure.RestartRequired -> markRestartRequired().copy(
        status = MainStatus.RestartRequired,
        busy = false,
    )
    ConnectFailure.PairingRequired -> copy(
        status = MainStatus.ConnectionPairingRequired,
        busy = false,
        connected = false,
    )
    ConnectFailure.PortUnavailable -> copy(
        status = MainStatus.ConnectionPortUnavailable,
        busy = false,
        connected = false,
    )
    ConnectFailure.Other -> copy(
        status = MainStatus.ConnectionFailed,
        busy = false,
        connected = false,
    )
}

/**
 * Whether the connection settings screen should auto-expand the pairing section for the given
 * status. [MainStatus.ConnectionFailed] is included because a generic TLS-level failure cannot
 * be distinguished from an unpaired device (see `classifyConnectFailure`), so this errs toward
 * showing the re-pairing path. [MainStatus.ConnectionPortUnavailable] is excluded because a
 * reachable-but-stale port is not a pairing problem, and forcing the section open there would
 * misdirect the user toward re-pairing instead of correcting the port.
 */
internal fun shouldExpandPairingSection(status: MainStatus): Boolean = when (status) {
    MainStatus.ConnectionPairingRequired, MainStatus.ConnectionFailed -> true
    else -> false
}

internal enum class MainStatusTone {
    Neutral,
    Progress,
    Success,
    Failure,
    Warning,
}

internal data class ConnectionStatusPresentation(
    val tone: MainStatusTone,
    val messageRes: Int,
)

internal fun connectionStatusPresentation(state: MainUiState): ConnectionStatusPresentation = when {
    state.restartRequired -> ConnectionStatusPresentation(
        tone = MainStatusTone.Warning,
        messageRes = R.string.restart_required,
    )
    state.connected -> ConnectionStatusPresentation(
        tone = MainStatusTone.Success,
        messageRes = R.string.connection_success,
    )
    else -> ConnectionStatusPresentation(
        tone = MainStatusTone.Neutral,
        messageRes = R.string.status_not_connected,
    )
}

internal fun mainStatusTone(status: MainStatus): MainStatusTone = when (status) {
    MainStatus.NotConnected -> MainStatusTone.Neutral
    MainStatus.Pairing,
    MainStatus.Connecting,
    MainStatus.Reading,
    MainStatus.SettingZero,
    MainStatus.SettingOne,
    MainStatus.CredentialResetting,
    -> MainStatusTone.Progress
    MainStatus.Paired,
    MainStatus.Connected,
    MainStatus.ReadComplete,
    MainStatus.SetZeroSuccess,
    MainStatus.SetOneSuccess,
    -> MainStatusTone.Success
    MainStatus.InvalidPairingPort,
    MainStatus.InvalidPairingCode,
    MainStatus.PairingFailed,
    MainStatus.InvalidConnectionPort,
    MainStatus.ConnectionFailed,
    MainStatus.ConnectionPairingRequired,
    MainStatus.ConnectionPortUnavailable,
    MainStatus.InvalidOutput,
    MainStatus.ReadTimeout,
    MainStatus.ReadTransportFailed,
    MainStatus.WriteReadBackMismatch,
    MainStatus.WriteInvalidOutput,
    MainStatus.WriteTimeout,
    MainStatus.WriteTransportFailed,
    MainStatus.CredentialResetFailed,
    -> MainStatusTone.Failure
    MainStatus.RestartRequired -> MainStatusTone.Warning
}

internal enum class ForcedShutterSwitchState {
    On,
    Off,
    Disabled,
}

internal fun forcedShutterSwitchState(value: ForcedSettingValue?): ForcedShutterSwitchState = when (value) {
    is ForcedSettingValue.Present -> when (value.raw) {
        "1" -> ForcedShutterSwitchState.On
        "0" -> ForcedShutterSwitchState.Off
        else -> ForcedShutterSwitchState.Disabled
    }
    null, ForcedSettingValue.NotSet -> ForcedShutterSwitchState.Disabled
}

internal class MainViewModel(
    private val session: LocalAdbSession,
    private val connectionPreferences: ConnectionPreferences,
) : ViewModel() {
    private val repository = ShutterRepository(session)
    private val mutableState = MutableStateFlow(
        MainUiState(connectionPort = connectionPreferences.restoreValidatedConnectionPort()),
    )
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    /**
     * Hint for the pairing section's default expanded/collapsed state only. `true` does not
     * mean pairing has ever succeeded (see [LocalAdbSession.hasStoredCredentials]), so callers
     * must always let the user open the section regardless of this value.
     */
    val hasStoredCredentialsAtStartup: Boolean = session.hasStoredCredentials()

    fun setPairingPort(value: String) {
        AdbInputValidator.acceptBoundedAsciiDigits(value, MAX_PORT_DIGITS)?.let { accepted ->
            mutableState.value = mutableState.value.copy(pairingPort = accepted)
        }
    }

    fun setConnectionPort(value: String) {
        AdbInputValidator.acceptBoundedAsciiDigits(value, MAX_PORT_DIGITS)?.let { accepted ->
            mutableState.value = mutableState.value.copy(connectionPort = accepted)
        }
    }

    fun pair(code: String) {
        if (mutableState.value.busy || mutableState.value.restartRequired) return
        val current = mutableState.value
        val port = AdbInputValidator.parsePort(current.pairingPort)
        when {
            port == null -> setStatus(MainStatus.InvalidPairingPort)
            !AdbInputValidator.isPairingCode(code) -> setStatus(MainStatus.InvalidPairingCode)
            else -> {
                mutableState.value = current.copy(status = MainStatus.Pairing, busy = true)
                session.pair(port, code) { result ->
                    mutableState.value = if (result.exceptionOrNull() is AdbRestartRequiredException) {
                        mutableState.value.markRestartRequired().copy(
                            status = MainStatus.RestartRequired,
                            busy = false,
                        )
                    } else {
                        mutableState.value.copy(
                            status = if (result.isSuccess) MainStatus.Paired else MainStatus.PairingFailed,
                            busy = false,
                        )
                    }
                }
            }
        }
    }

    fun connect() {
        if (mutableState.value.busy || mutableState.value.restartRequired) return
        val current = mutableState.value
        val port = AdbInputValidator.parsePort(current.connectionPort)
        if (port == null) {
            setStatus(MainStatus.InvalidConnectionPort)
            return
        }
        mutableState.value = current.copy(status = MainStatus.Connecting, busy = true)
        session.connect(port) { result ->
            connectionPreferences.saveConnectionPortIfConnected(result.isSuccess, current.connectionPort)
            mutableState.value = result.fold(
                onSuccess = { mutableState.value.markConnected() },
                onFailure = { exception ->
                    mutableState.value.applyConnectFailure(classifyConnectFailure(exception))
                },
            )
        }
    }

    fun openHome() {
        mutableState.value = mutableState.value.openHome()
    }

    fun openConnectionSettings() {
        mutableState.value = mutableState.value.openConnectionSettings()
    }

    fun openCameraSettings() {
        mutableState.value = mutableState.value.openCameraSettings()
    }

    fun read() {
        if (mutableState.value.busy || mutableState.value.restartRequired) return
        mutableState.value = mutableState.value.copy(status = MainStatus.Reading, busy = true)
        repository.read { result ->
            mutableState.value = when (result) {
                is ShutterReadResult.Success -> mutableState.value.copy(
                    currentValue = result.value,
                    status = MainStatus.ReadComplete,
                    busy = false,
                )
                is ShutterReadResult.Failure -> mutableState.value.copy(
                    currentValue = null,
                    status = when (result.error) {
                        ShutterReadError.InvalidOutput -> MainStatus.InvalidOutput
                        ShutterReadError.Timeout -> MainStatus.ReadTimeout
                        ShutterReadError.RestartRequired -> MainStatus.RestartRequired
                        ShutterReadError.TransportFailure -> MainStatus.ReadTransportFailed
                    },
                    busy = false,
                ).let { state ->
                    if (result.error is ShutterReadError.RestartRequired) {
                        state.markRestartRequired()
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun setZero() {
        if (mutableState.value.busy || mutableState.value.restartRequired) return
        mutableState.value = mutableState.value.copy(status = MainStatus.SettingZero, busy = true)
        repository.setZero { result -> applyWriteResult(result, MainStatus.SetZeroSuccess) }
    }

    fun setOne() {
        if (mutableState.value.busy || mutableState.value.restartRequired) return
        mutableState.value = mutableState.value.copy(status = MainStatus.SettingOne, busy = true)
        repository.setOne { result -> applyWriteResult(result, MainStatus.SetOneSuccess) }
    }

    fun resetCredentials() {
        if (mutableState.value.busy || mutableState.value.restartRequired) return
        mutableState.value = mutableState.value.copy(status = MainStatus.CredentialResetting, busy = true)
        repository.resetCredentials { result ->
            // A full re-pairing is required either way (see markRestartRequired below), so the
            // saved connection port is stale regardless of whether the reset itself succeeded.
            connectionPreferences.clearConnectionPort()
            mutableState.value = mutableState.value.copy(
                status = if (result.isSuccess) {
                    MainStatus.RestartRequired
                } else {
                    MainStatus.CredentialResetFailed
                },
                currentValue = null,
                busy = false,
            ).markRestartRequired()
        }
    }

    private fun applyWriteResult(
        result: ShutterWriteResult,
        successStatus: MainStatus,
    ) {
        mutableState.value = when (result) {
            is ShutterWriteResult.Success ->
                mutableState.value.copy(
                    currentValue = result.value,
                    status = successStatus,
                    busy = false,
                )
            is ShutterWriteResult.ReadBackMismatch ->
                mutableState.value.copy(
                    currentValue = result.actual,
                    status = MainStatus.WriteReadBackMismatch,
                    busy = false,
                )
            ShutterWriteResult.InvalidOutput ->
                mutableState.value.copy(
                    currentValue = null,
                    status = MainStatus.WriteInvalidOutput,
                    busy = false,
                )
            ShutterWriteResult.Timeout ->
                mutableState.value.copy(
                    currentValue = null,
                    status = MainStatus.WriteTimeout,
                    busy = false,
                )
            ShutterWriteResult.RestartRequired ->
                mutableState.value.markRestartRequired().copy(
                    currentValue = null,
                    status = MainStatus.RestartRequired,
                    busy = false,
                )
            ShutterWriteResult.TransportFailure ->
                mutableState.value.copy(
                    currentValue = null,
                    status = MainStatus.WriteTransportFailed,
                    busy = false,
                )
        }
    }

    private fun setStatus(status: MainStatus) {
        mutableState.value = mutableState.value.copy(status = status, busy = false)
    }

    override fun onCleared() {
        session.disconnect()
        super.onCleared()
    }

    companion object {
        private const val MAX_PORT_DIGITS = 5

        fun factory(session: LocalAdbSession, connectionPreferences: ConnectionPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MainViewModel::class.java))
                    return MainViewModel(session, connectionPreferences) as T
                }
            }
    }
}
