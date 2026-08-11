package io.github.fumtas1k.localtweaks.adb

object AdbInputValidator {
    fun parsePort(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
        return value.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    fun isPairingCode(raw: String): Boolean = raw.matches(Regex("[0-9]{6}"))
}
