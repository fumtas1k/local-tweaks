package io.github.fumtas1k.localtweaks.feature.shutter

sealed interface ForcedSettingValue {
    data object NotSet : ForcedSettingValue
    data class Present(val raw: String) : ForcedSettingValue
}

object ForcedSettingParser {
    fun parse(output: String): ForcedSettingValue {
        if (output.toByteArray(Charsets.UTF_8).size > MAX_OUTPUT_BYTES) {
            throw ProtocolException("settings output is too large")
        }
        val raw = output.trim()
        if (raw.isEmpty() || raw.any { it == '\n' || it == '\r' }) {
            throw ProtocolException("invalid settings output")
        }
        return if (raw == "null") ForcedSettingValue.NotSet else ForcedSettingValue.Present(raw)
    }

    private const val MAX_OUTPUT_BYTES = 128
}

class ProtocolException(message: String) : IllegalArgumentException(message)
