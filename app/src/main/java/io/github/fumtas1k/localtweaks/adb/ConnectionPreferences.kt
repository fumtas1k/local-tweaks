package io.github.fumtas1k.localtweaks.adb

import android.content.Context
import androidx.core.content.edit

/**
 * Persists only the connection port across process restarts.
 *
 * The pairing port is never persisted here: it changes every time the pairing dialog is
 * reopened, so a stale saved value would only invite mistakes. The pairing code is never
 * persisted anywhere, per requirements.md.
 */
internal interface ConnectionPreferences {
    fun loadConnectionPort(): String
    fun saveConnectionPort(port: String)
    fun clearConnectionPort()
}

/** Restores the persisted port, re-validating it the same way manual input is validated. */
internal fun ConnectionPreferences.restoreValidatedConnectionPort(): String {
    val stored = loadConnectionPort()
    return if (AdbInputValidator.parsePort(stored) != null) stored else ""
}

/** Saves [port] only when [connectSucceeded] is true; a failed or aborted attempt never persists. */
internal fun ConnectionPreferences.saveConnectionPortIfConnected(connectSucceeded: Boolean, port: String) {
    if (connectSucceeded) saveConnectionPort(port)
}

/**
 * [android.content.SharedPreferences]-backed implementation, stored with [Context.MODE_PRIVATE].
 * The app's data extraction rules exclude the `sharedpref` domain from cloud backup and
 * device-to-device transfer, and `android:allowBackup` is `false`, so this file never leaves
 * the device.
 */
internal class SharedPreferencesConnectionPreferences(context: Context) : ConnectionPreferences {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadConnectionPort(): String = prefs.getString(KEY_CONNECTION_PORT, "") ?: ""

    override fun saveConnectionPort(port: String) {
        prefs.edit { putString(KEY_CONNECTION_PORT, port) }
    }

    override fun clearConnectionPort() {
        prefs.edit { remove(KEY_CONNECTION_PORT) }
    }

    private companion object {
        const val PREFS_NAME = "connection_preferences"
        const val KEY_CONNECTION_PORT = "connection_port"
    }
}
