package io.github.fumtas1k.localtweaks.feature.shutter

import io.github.fumtas1k.localtweaks.adb.LocalAdbSession

/** Domain-facing read-only API; command details stay inside the ADB package. */
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
}

sealed interface ShutterReadResult {
    data class Success(val value: ForcedSettingValue) : ShutterReadResult
    data class Failure(val error: ShutterReadError) : ShutterReadResult
}

sealed interface ShutterReadError {
    data object InvalidOutput : ShutterReadError
    data object TransportFailure : ShutterReadError
}

internal fun mapReadFailure(error: Throwable): ShutterReadError =
    if (error is LocalAdbSession.ProtocolException) {
        ShutterReadError.InvalidOutput
    } else {
        ShutterReadError.TransportFailure
    }
