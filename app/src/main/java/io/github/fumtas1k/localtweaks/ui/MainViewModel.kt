package io.github.fumtas1k.localtweaks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.fumtas1k.localtweaks.adb.AdbInputValidator
import io.github.fumtas1k.localtweaks.adb.LocalAdbSession
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
    CredentialResetRestartRequired,
    CredentialResetFailed,
}

internal data class MainUiState(
    val pairingPort: String = "",
    val connectionPort: String = "",
    val status: MainStatus = MainStatus.NotConnected,
    val currentValue: ForcedSettingValue? = null,
    val busy: Boolean = false,
)

internal class MainViewModel(private val session: LocalAdbSession) : ViewModel() {
    private val repository = ShutterRepository(session)
    private val mutableState = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    fun setPairingPort(value: String) {
        mutableState.value = mutableState.value.copy(pairingPort = value)
    }

    fun setConnectionPort(value: String) {
        mutableState.value = mutableState.value.copy(connectionPort = value)
    }

    fun pair(code: String) {
        if (mutableState.value.busy) return
        val current = mutableState.value
        val port = AdbInputValidator.parsePort(current.pairingPort)
        when {
            port == null -> setStatus(MainStatus.InvalidPairingPort)
            !AdbInputValidator.isPairingCode(code) -> setStatus(MainStatus.InvalidPairingCode)
            else -> {
                mutableState.value = current.copy(status = MainStatus.Pairing, busy = true)
                session.pair(port, code) { result ->
                    mutableState.value = mutableState.value.copy(
                        status = if (result.isSuccess) MainStatus.Paired else MainStatus.PairingFailed,
                        busy = false,
                    )
                }
            }
        }
    }

    fun connect() {
        if (mutableState.value.busy) return
        val current = mutableState.value
        val port = AdbInputValidator.parsePort(current.connectionPort)
        if (port == null) {
            setStatus(MainStatus.InvalidConnectionPort)
            return
        }
        mutableState.value = current.copy(status = MainStatus.Connecting, busy = true)
        session.connect(port) { result ->
            mutableState.value = mutableState.value.copy(
                status = if (result.isSuccess) MainStatus.Connected else MainStatus.ConnectionFailed,
                busy = false,
            )
        }
    }

    fun read() {
        if (mutableState.value.busy) return
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
                        ShutterReadError.TransportFailure -> MainStatus.ReadTransportFailed
                    },
                    busy = false,
                )
            }
        }
    }

    fun setZero() {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(status = MainStatus.SettingZero, busy = true)
        repository.setZero { result -> applyWriteResult(result, MainStatus.SetZeroSuccess) }
    }

    fun setOne() {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(status = MainStatus.SettingOne, busy = true)
        repository.setOne { result -> applyWriteResult(result, MainStatus.SetOneSuccess) }
    }

    fun resetCredentials() {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(status = MainStatus.CredentialResetting, busy = true)
        repository.resetCredentials { result ->
            mutableState.value = mutableState.value.copy(
                status = if (result.isSuccess) {
                    MainStatus.CredentialResetRestartRequired
                } else {
                    MainStatus.CredentialResetFailed
                },
                currentValue = null,
                busy = false,
            )
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
        fun factory(session: LocalAdbSession): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MainViewModel::class.java))
                    return MainViewModel(session) as T
                }
            }
    }
}
