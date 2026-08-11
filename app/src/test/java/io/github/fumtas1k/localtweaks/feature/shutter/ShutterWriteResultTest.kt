package io.github.fumtas1k.localtweaks.feature.shutter

import io.github.fumtas1k.localtweaks.adb.AdbRestartRequiredException
import io.github.fumtas1k.localtweaks.adb.LocalAdbSession
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ShutterWriteResultTest {
    @Test fun setZeroSucceedsOnlyWhenReadBackIsZero() {
        assertEquals(
            ShutterWriteResult.Success(ForcedSettingValue.Present("0")),
            evaluateWriteReadBack("0", ForcedSettingValue.Present("0")),
        )
    }

    @Test fun setOneSucceedsOnlyWhenReadBackIsOne() {
        assertEquals(
            ShutterWriteResult.Success(ForcedSettingValue.Present("1")),
            evaluateWriteReadBack("1", ForcedSettingValue.Present("1")),
        )
    }

    @Test fun notSetReadBackIsMismatch() {
        assertEquals(
            ShutterWriteResult.ReadBackMismatch("0", ForcedSettingValue.NotSet),
            evaluateWriteReadBack("0", ForcedSettingValue.NotSet),
        )
    }

    @Test fun unknownReadBackIsMismatchAndPreserved() {
        val actual = ForcedSettingValue.Present("unexpected")
        assertEquals(ShutterWriteResult.ReadBackMismatch("1", actual), evaluateWriteReadBack("1", actual))
    }

    @Test fun malformedReadBackIsInvalidOutput() {
        assertEquals(
            ShutterWriteResult.InvalidOutput,
            mapWriteResult("0", Result.success("0\n1")),
        )
    }

    @Test fun oversizedReadBackIsInvalidOutput() {
        assertEquals(
            ShutterWriteResult.InvalidOutput,
            mapWriteResult("1", Result.success("x".repeat(129))),
        )
    }

    @Test fun transportFailureDoesNotExposeItsMessage() {
        assertEquals(
            ShutterWriteResult.TransportFailure,
            mapWriteResult("0", Result.failure(IOException("private transport detail"))),
        )
    }

    @Test fun protocolFailureIsInvalidOutput() {
        assertEquals(
            ShutterWriteResult.InvalidOutput,
            mapWriteResult("0", Result.failure(LocalAdbSession.ProtocolException("response too large"))),
        )
    }

    @Test fun timeoutIsDistinctFromTransportFailure() {
        assertEquals(
            ShutterWriteResult.Timeout,
            mapWriteResult("0", Result.failure(LocalAdbSession.AdbTimeoutException())),
        )
    }

    @Test fun restartRequiredIsDistinctFromTransportFailure() {
        assertEquals(
            ShutterWriteResult.RestartRequired,
            mapWriteResult(
                "0",
                Result.failure(AdbRestartRequiredException()),
            ),
        )
    }
}
