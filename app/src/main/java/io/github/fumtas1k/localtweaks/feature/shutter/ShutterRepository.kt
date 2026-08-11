package io.github.fumtas1k.localtweaks.feature.shutter

import io.github.fumtas1k.localtweaks.adb.AdbRestartRequiredException
import io.github.fumtas1k.localtweaks.adb.LocalAdbSession

/** Domain-facing typed settings API; command details stay inside the ADB package. */
internal class ShutterRepository(private val session: LocalAdbSession) {
    fun read(onComplete: (ShutterReadResult) -> Unit) {
        session.read { result ->
            result.fold(
                onSuccess = { raw ->
                    onComplete(
                        runCatching { ForcedSettingParser.parse(raw) }.fold(
                            onSuccess = { ShutterReadResult.Success(it) },
                            onFailure = { ShutterReadResult.Failure(ShutterReadError.InvalidOutput) },
                        ),
                    )
                },
                onFailure = { onComplete(ShutterReadResult.Failure(mapReadFailure(it))) },
            )
        }
    }

    fun setZero(onComplete: (ShutterWriteResult) -> Unit) {
        session.setZero { result -> onComplete(mapWriteResult("0", result)) }
    }

    fun setOne(onComplete: (ShutterWriteResult) -> Unit) {
        session.setOne { result -> onComplete(mapWriteResult("1", result)) }
    }

    fun resetCredentials(onComplete: (Result<Unit>) -> Unit) = session.resetCredentials(onComplete)
}

sealed interface ShutterReadResult {
    data class Success(val value: ForcedSettingValue) : ShutterReadResult
    data class Failure(val error: ShutterReadError) : ShutterReadResult
}

sealed interface ShutterReadError {
    data object InvalidOutput : ShutterReadError
    data object Timeout : ShutterReadError
    data object RestartRequired : ShutterReadError
    data object TransportFailure : ShutterReadError
}

sealed interface ShutterWriteResult {
    data class Success(val value: ForcedSettingValue.Present) : ShutterWriteResult
    data class ReadBackMismatch(
        val expected: String,
        val actual: ForcedSettingValue,
    ) : ShutterWriteResult
    data object InvalidOutput : ShutterWriteResult
    data object Timeout : ShutterWriteResult
    data object RestartRequired : ShutterWriteResult
    data object TransportFailure : ShutterWriteResult
}

internal fun evaluateWriteReadBack(
    expected: String,
    actual: ForcedSettingValue,
): ShutterWriteResult = when (actual) {
    is ForcedSettingValue.Present ->
        if (actual.raw == expected) ShutterWriteResult.Success(actual)
        else ShutterWriteResult.ReadBackMismatch(expected, actual)
    ForcedSettingValue.NotSet -> ShutterWriteResult.ReadBackMismatch(expected, actual)
}

internal fun mapWriteResult(expected: String, result: Result<String>): ShutterWriteResult =
    result.fold(
        onSuccess = { raw ->
            runCatching { ForcedSettingParser.parse(raw) }.fold(
                onSuccess = { actual -> evaluateWriteReadBack(expected, actual) },
                onFailure = { ShutterWriteResult.InvalidOutput },
            )
        },
        onFailure = { error ->
            when (error) {
                is AdbRestartRequiredException ->
                    ShutterWriteResult.RestartRequired
                is LocalAdbSession.AdbTimeoutException -> ShutterWriteResult.Timeout
                is LocalAdbSession.ProtocolException -> ShutterWriteResult.InvalidOutput
                else -> ShutterWriteResult.TransportFailure
            }
        },
    )

internal fun mapReadFailure(error: Throwable): ShutterReadError =
    when (error) {
        is AdbRestartRequiredException ->
            ShutterReadError.RestartRequired
        is LocalAdbSession.AdbTimeoutException -> ShutterReadError.Timeout
        is LocalAdbSession.ProtocolException -> ShutterReadError.InvalidOutput
        else -> ShutterReadError.TransportFailure
    }
