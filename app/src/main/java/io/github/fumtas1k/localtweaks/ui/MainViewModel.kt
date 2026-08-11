package io.github.fumtas1k.localtweaks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.fumtas1k.localtweaks.adb.AdbInputValidator
import io.github.fumtas1k.localtweaks.adb.LocalAdbSession
import io.github.fumtas1k.localtweaks.feature.shutter.ForcedSettingValue
import io.github.fumtas1k.localtweaks.feature.shutter.ShutterReadError
import io.github.fumtas1k.localtweaks.feature.shutter.ShutterReadResult
import io.github.fumtas1k.localtweaks.feature.shutter.ShutterRepository
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
    ReadTransportFailed,
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
                        ShutterReadError.TransportFailure -> MainStatus.ReadTransportFailed
                    },
                    busy = false,
                )
            }
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
