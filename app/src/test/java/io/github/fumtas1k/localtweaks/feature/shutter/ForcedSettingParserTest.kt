package io.github.fumtas1k.localtweaks.feature.shutter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ForcedSettingParserTest {
    @Test fun readReturnsNotSetForNullOutput() {
        assertEquals(ForcedSettingValue.NotSet, ForcedSettingParser.parse(" null\r\n"))
    }

    @Test fun readPreservesUnknownTrimmedRawValue() {
        assertEquals(ForcedSettingValue.Present("unexpected"), ForcedSettingParser.parse(" unexpected \n"))
    }

    @Test fun readPreservesZeroAndOneAsPresent() {
        assertEquals(ForcedSettingValue.Present("0"), ForcedSettingParser.parse("0"))
        assertEquals(ForcedSettingValue.Present("1"), ForcedSettingParser.parse("1"))
    }

    @Test fun readRejectsEmptyOutput() {
        assertThrows(ProtocolException::class.java) { ForcedSettingParser.parse(" \r\n ") }
    }

    @Test fun readRejectsMultipleLines() {
        assertThrows(ProtocolException::class.java) { ForcedSettingParser.parse("0\n1") }
    }

    @Test fun readRejectsOutputOver128Utf8Bytes() {
        assertThrows(ProtocolException::class.java) { ForcedSettingParser.parse("x".repeat(129)) }
    }
}
