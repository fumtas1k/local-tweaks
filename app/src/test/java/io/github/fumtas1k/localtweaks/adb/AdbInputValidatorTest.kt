package io.github.fumtas1k.localtweaks.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbInputValidatorTest {
    @Test fun parsePortAcceptsOnlyAsciiDigitsInRange() {
        assertEquals(1, AdbInputValidator.parsePort(" 1 "))
        assertEquals(65535, AdbInputValidator.parsePort("65535"))
        listOf("", "0", "65536", "-1", "１", "127.0.0.1", "tcp://1").forEach {
            assertEquals(null, AdbInputValidator.parsePort(it))
        }
    }

    @Test fun pairingCodeRequiresSixAsciiDigits() {
        assertTrue(AdbInputValidator.isPairingCode("012345"))
        assertFalse(AdbInputValidator.isPairingCode("12345"))
        assertFalse(AdbInputValidator.isPairingCode("１２３４５６"))
        assertFalse(AdbInputValidator.isPairingCode("123456\n"))
    }
}
