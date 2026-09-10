package io.github.fumtas1k.localtweaks.adb

import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.IOException
import java.net.ConnectException
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectFailureTest {
    @Test fun restartRequiredExceptionClassifiesAsRestartRequired() {
        assertEquals(ConnectFailure.RestartRequired, classifyConnectFailure(AdbRestartRequiredException()))
    }

    @Test fun pairingRequiredExceptionClassifiesAsPairingRequired() {
        assertEquals(
            ConnectFailure.PairingRequired,
            classifyConnectFailure(AdbPairingRequiredException("pairing required")),
        )
    }

    @Test fun authenticationFailedExceptionClassifiesAsPairingRequired() {
        assertEquals(ConnectFailure.PairingRequired, classifyConnectFailure(AdbAuthenticationFailedException()))
    }

    @Test fun directConnectExceptionClassifiesAsPortUnavailable() {
        assertEquals(ConnectFailure.PortUnavailable, classifyConnectFailure(ConnectException()))
    }

    @Test fun connectExceptionAsImmediateCauseClassifiesAsPortUnavailable() {
        val error = IOException().apply { initCause(ConnectException()) }

        assertEquals(ConnectFailure.PortUnavailable, classifyConnectFailure(error))
    }

    @Test fun connectExceptionDeepInCauseChainClassifiesAsPortUnavailable() {
        val root = ConnectException()
        val middle = IOException("wrapped").apply { initCause(root) }
        val outer = IOException("connection failed").apply { initCause(middle) }

        assertEquals(ConnectFailure.PortUnavailable, classifyConnectFailure(outer))
    }

    @Test fun genericConnectionFailedMessageClassifiesAsOther() {
        assertEquals(ConnectFailure.Other, classifyConnectFailure(IOException("Connection failed")))
    }

    @Test fun lowercaseConnectionFailedMessageClassifiesAsOther() {
        assertEquals(ConnectFailure.Other, classifyConnectFailure(IOException("connection failed")))
    }

    @Test fun cyclicCauseChainTerminatesAndClassifiesAsOther() {
        // Throwable.initCause forbids self-reference (cause == this) but allows two distinct
        // exceptions to reference each other, forming an indirect cycle.
        val first = IOException("first")
        val second = IOException("second")
        first.initCause(second)
        second.initCause(first)

        assertEquals(ConnectFailure.Other, classifyConnectFailure(first))
    }
}
