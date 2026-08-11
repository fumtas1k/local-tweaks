package io.github.fumtas1k.localtweaks.adb

import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeConnectionPreferences(initial: String = "") : ConnectionPreferences {
    var stored: String = initial
        private set

    override fun loadConnectionPort(): String = stored

    override fun saveConnectionPort(port: String) {
        stored = port
    }

    override fun clearConnectionPort() {
        stored = ""
    }
}

class ConnectionPreferencesTest {
    @Test fun restoresAValidStoredPort() {
        val prefs = FakeConnectionPreferences(initial = "5555")

        assertEquals("5555", prefs.restoreValidatedConnectionPort())
    }

    @Test fun restoresEmptyStringWhenNothingStored() {
        val prefs = FakeConnectionPreferences()

        assertEquals("", prefs.restoreValidatedConnectionPort())
    }

    @Test fun restoresEmptyStringWhenStoredPortIsOutOfRange() {
        val prefs = FakeConnectionPreferences(initial = "70000")

        assertEquals("", prefs.restoreValidatedConnectionPort())
    }

    @Test fun restoresEmptyStringWhenStoredPortIsZero() {
        val prefs = FakeConnectionPreferences(initial = "0")

        assertEquals("", prefs.restoreValidatedConnectionPort())
    }

    @Test fun restoresEmptyStringWhenStoredValueIsNotNumeric() {
        val prefs = FakeConnectionPreferences(initial = "12ab")

        assertEquals("", prefs.restoreValidatedConnectionPort())
    }

    @Test fun savesPortOnlyWhenConnectSucceeded() {
        val prefs = FakeConnectionPreferences()

        prefs.saveConnectionPortIfConnected(connectSucceeded = true, port = "5555")

        assertEquals("5555", prefs.stored)
    }

    @Test fun doesNotSavePortWhenConnectFailed() {
        val prefs = FakeConnectionPreferences()

        prefs.saveConnectionPortIfConnected(connectSucceeded = false, port = "5555")

        assertEquals("", prefs.stored)
    }

    @Test fun doesNotOverwriteStoredPortWhenConnectFailed() {
        val prefs = FakeConnectionPreferences(initial = "5555")

        prefs.saveConnectionPortIfConnected(connectSucceeded = false, port = "9999")

        assertEquals("5555", prefs.stored)
    }

    @Test fun clearRemovesStoredPort() {
        val prefs = FakeConnectionPreferences(initial = "5555")

        prefs.clearConnectionPort()

        assertEquals("", prefs.stored)
    }
}
