package io.github.fumtas1k.localtweaks.ui

import io.github.fumtas1k.localtweaks.R
import io.github.fumtas1k.localtweaks.adb.ConnectFailure
import io.github.fumtas1k.localtweaks.feature.shutter.ForcedSettingValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {
    @Test fun initialStateOpensHome() {
        assertEquals(MainScreen.Home, MainUiState().screen)
    }

    @Test fun successfulConnectionOpensHome() {
        val state = MainUiState(
            currentValue = ForcedSettingValue.Present("0"),
            busy = true,
        ).markConnected()

        assertEquals(MainScreen.Home, state.screen)
        assertTrue(state.connected)
        assertFalse(state.busy)
        assertEquals(MainStatus.Connected, state.status)
        assertNull(state.currentValue)
    }

    @Test fun disconnectedStateCannotOpenCameraSettings() {
        val state = MainUiState().openCameraSettings()

        assertEquals(MainScreen.Home, state.screen)
    }

    @Test fun connectedStateCanOpenCameraSettingsFromHome() {
        val state = MainUiState(connected = true).openCameraSettings()

        assertEquals(MainScreen.CameraSettings, state.screen)
    }

    @Test fun openingConnectionSettingsPreservesInputsAndValue() {
        val state = MainUiState(
            pairingPort = "1234",
            connectionPort = "5678",
            currentValue = ForcedSettingValue.Present("unknown"),
            connected = true,
            screen = MainScreen.CameraSettings,
        ).openConnectionSettings()

        assertEquals(MainScreen.ConnectionSettings, state.screen)
        assertEquals("1234", state.pairingPort)
        assertEquals("5678", state.connectionPort)
        assertEquals(ForcedSettingValue.Present("unknown"), state.currentValue)
    }

    @Test fun openingConnectionSettingsNormalizesConnectedStatus() {
        val state = MainUiState(
            connected = true,
            status = MainStatus.ReadComplete,
            screen = MainScreen.CameraSettings,
        ).openConnectionSettings()

        assertEquals(MainScreen.ConnectionSettings, state.screen)
        assertEquals(MainStatus.Connected, state.status)
    }

    @Test fun restartRequiredReturnsToConnectionSettingsAndBlocksHome() {
        val state = MainUiState(
            connected = true,
            screen = MainScreen.CameraSettings,
        ).markRestartRequired()

        assertEquals(MainScreen.ConnectionSettings, state.screen)
        assertEquals(MainScreen.ConnectionSettings, state.openHome().screen)
        assertTrue(state.restartRequired)
        assertFalse(state.connected)
    }

    @Test fun forcedSettingOneEnablesSwitchOn() {
        assertEquals(
            ForcedShutterSwitchState.On,
            forcedShutterSwitchState(ForcedSettingValue.Present("1")),
        )
    }

    @Test fun forcedSettingZeroEnablesSwitchOff() {
        assertEquals(
            ForcedShutterSwitchState.Off,
            forcedShutterSwitchState(ForcedSettingValue.Present("0")),
        )
    }

    @Test fun notSetDisablesSwitch() {
        assertEquals(
            ForcedShutterSwitchState.Disabled,
            forcedShutterSwitchState(ForcedSettingValue.NotSet),
        )
    }

    @Test fun unknownRawValueDisablesSwitch() {
        assertEquals(
            ForcedShutterSwitchState.Disabled,
            forcedShutterSwitchState(ForcedSettingValue.Present("unknown")),
        )
    }

    @Test fun missingValueDisablesSwitch() {
        assertEquals(ForcedShutterSwitchState.Disabled, forcedShutterSwitchState(null))
    }

    @Test fun everyMainStatusHasATone() {
        for (status in MainStatus.entries) {
            assertNotNull(mainStatusTone(status))
        }
    }

    @Test fun everyToneIsReachedByAtLeastOneStatus() {
        val reachedTones = MainStatus.entries.map(::mainStatusTone).toSet()
        assertEquals(MainStatusTone.entries.toSet(), reachedTones)
    }

    @Test fun notConnectedIsNeutral() {
        assertEquals(MainStatusTone.Neutral, mainStatusTone(MainStatus.NotConnected))
    }

    @Test fun inFlightStatusesAreProgress() {
        val inFlight = setOf(
            MainStatus.Pairing,
            MainStatus.Connecting,
            MainStatus.Reading,
            MainStatus.SettingZero,
            MainStatus.SettingOne,
            MainStatus.CredentialResetting,
        )
        for (status in inFlight) {
            assertEquals(MainStatusTone.Progress, mainStatusTone(status))
        }
    }

    @Test fun completedOperationsAreSuccess() {
        val completed = setOf(
            MainStatus.Paired,
            MainStatus.Connected,
            MainStatus.ReadComplete,
            MainStatus.SetZeroSuccess,
            MainStatus.SetOneSuccess,
        )
        for (status in completed) {
            assertEquals(MainStatusTone.Success, mainStatusTone(status))
        }
    }

    @Test fun errorsAndInvalidInputAreFailure() {
        val failures = setOf(
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
        )
        for (status in failures) {
            assertEquals(MainStatusTone.Failure, mainStatusTone(status))
        }
    }

    @Test fun restartRequiredIsWarning() {
        assertEquals(MainStatusTone.Warning, mainStatusTone(MainStatus.RestartRequired))
    }

    @Test fun connectedHomeStatusUsesConnectionSuccessRegardlessOfSharedStatus() {
        val presentation = connectionStatusPresentation(
            MainUiState(
                connected = true,
                status = MainStatus.ReadComplete,
            ),
        )

        assertEquals(MainStatusTone.Success, presentation.tone)
        assertEquals(R.string.connection_success, presentation.messageRes)
    }

    @Test fun restartRequiredConnectFailureForcesRestartAndConnectionSettings() {
        val state = MainUiState(
            connected = true,
            busy = true,
            screen = MainScreen.CameraSettings,
        ).applyConnectFailure(ConnectFailure.RestartRequired)

        assertEquals(MainStatus.RestartRequired, state.status)
        assertFalse(state.busy)
        assertFalse(state.connected)
        assertTrue(state.restartRequired)
        assertEquals(MainScreen.ConnectionSettings, state.screen)
    }

    @Test fun pairingRequiredConnectFailureClearsConnectedAndBusy() {
        val state = MainUiState(connected = true, busy = true).applyConnectFailure(ConnectFailure.PairingRequired)

        assertEquals(MainStatus.ConnectionPairingRequired, state.status)
        assertFalse(state.busy)
        assertFalse(state.connected)
        assertFalse(state.restartRequired)
    }

    @Test fun portUnavailableConnectFailureClearsConnectedAndBusy() {
        val state = MainUiState(connected = true, busy = true).applyConnectFailure(ConnectFailure.PortUnavailable)

        assertEquals(MainStatus.ConnectionPortUnavailable, state.status)
        assertFalse(state.busy)
        assertFalse(state.connected)
        assertFalse(state.restartRequired)
    }

    @Test fun otherConnectFailureClearsConnectedAndBusy() {
        val state = MainUiState(connected = true, busy = true).applyConnectFailure(ConnectFailure.Other)

        assertEquals(MainStatus.ConnectionFailed, state.status)
        assertFalse(state.busy)
        assertFalse(state.connected)
        assertFalse(state.restartRequired)
    }

    @Test fun pairingRequiredAndGenericFailureExpandPairingSection() {
        assertTrue(shouldExpandPairingSection(MainStatus.ConnectionPairingRequired))
        assertTrue(shouldExpandPairingSection(MainStatus.ConnectionFailed))
    }

    @Test fun portUnavailableAndOtherStatusesDoNotExpandPairingSection() {
        assertFalse(shouldExpandPairingSection(MainStatus.ConnectionPortUnavailable))
        assertFalse(shouldExpandPairingSection(MainStatus.NotConnected))
        assertFalse(shouldExpandPairingSection(MainStatus.Connected))
        assertFalse(shouldExpandPairingSection(MainStatus.Connecting))
    }
}
