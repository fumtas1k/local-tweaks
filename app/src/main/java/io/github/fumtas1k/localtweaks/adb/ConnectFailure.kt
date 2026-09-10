package io.github.fumtas1k.localtweaks.adb

import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.net.ConnectException

/**
 * Coarse classification of [LocalAdbSession.connect] failures, used to choose UI guidance.
 * This is the only place in the codebase that inspects libadb-android's exception types; UI
 * code must depend only on this enum, never on the library's own exception classes.
 */
internal enum class ConnectFailure {
    /** Credential recovery requires a clean process restart before any further ADB call. */
    RestartRequired,

    /** Re-pairing is required, or very likely required, before a connection can succeed. */
    PairingRequired,

    /** Nothing is listening on the given loopback port (e.g. a stale connection port). */
    PortUnavailable,

    /** Any other connection failure; the cause is not distinguishable from the above. */
    Other,
}

/** Cause chains are walked at most this many links to guard against unbounded/cyclic chains. */
private const val MAX_CAUSE_CHAIN_DEPTH = 8

/**
 * Classifies a [LocalAdbSession.connect] failure using only the exception's type and cause
 * chain - never its message - so no ADB/library detail is ever surfaced to the UI or logs.
 */
internal fun classifyConnectFailure(error: Throwable): ConnectFailure = when {
    error is AdbRestartRequiredException -> ConnectFailure.RestartRequired
    error is AdbPairingRequiredException || error is AdbAuthenticationFailedException ->
        ConnectFailure.PairingRequired
    hasConnectExceptionInCauseChain(error) -> ConnectFailure.PortUnavailable
    else -> ConnectFailure.Other
}

private fun hasConnectExceptionInCauseChain(error: Throwable): Boolean {
    val visited = HashSet<Throwable>()
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < MAX_CAUSE_CHAIN_DEPTH && visited.add(current)) {
        if (current is ConnectException) return true
        current = current.cause
        depth++
    }
    return false
}
