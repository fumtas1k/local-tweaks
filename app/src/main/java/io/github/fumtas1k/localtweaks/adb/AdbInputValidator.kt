package io.github.fumtas1k.localtweaks.adb

object AdbInputValidator {
    fun parsePort(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > MAX_PORT_DIGITS || value.any { it !in '0'..'9' }) return null
        return value.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    fun isPairingCode(raw: String): Boolean = raw.matches(Regex("[0-9]{6}"))

    /** Returns the new field value only when it is bounded ASCII decimal input. */
    fun acceptBoundedAsciiDigits(raw: String, maxLength: Int): String? =
        raw.takeIf { it.length <= maxLength && it.all { character -> character in '0'..'9' } }

    private const val MAX_PORT_DIGITS = 5
}
