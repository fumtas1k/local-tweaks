package io.github.fumtas1k.localtweaks.feature.shutter

import io.github.fumtas1k.localtweaks.adb.AdbRestartRequiredException
import io.github.fumtas1k.localtweaks.adb.LocalAdbSession
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ShutterReadErrorTest {
    @Test fun oversizedOrMalformedTransportOutputIsInvalidOutput() {
        assertEquals(
            ShutterReadError.InvalidOutput,
            mapReadFailure(LocalAdbSession.ProtocolException("response is too large")),
        )
    }

    @Test fun connectionFailureIsTransportFailure() {
        assertEquals(ShutterReadError.TransportFailure, mapReadFailure(IOException("connection failed")))
    }

    @Test fun timeoutIsDistinctFromTransportFailure() {
        assertEquals(
            ShutterReadError.Timeout,
            mapReadFailure(LocalAdbSession.AdbTimeoutException()),
        )
    }

    @Test fun restartRequiredIsDistinctFromTransportFailure() {
        assertEquals(
            ShutterReadError.RestartRequired,
            mapReadFailure(AdbRestartRequiredException()),
        )
    }
}
