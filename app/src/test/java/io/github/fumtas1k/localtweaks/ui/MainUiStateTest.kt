package io.github.fumtas1k.localtweaks.ui

import io.github.fumtas1k.localtweaks.feature.shutter.ForcedSettingValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
