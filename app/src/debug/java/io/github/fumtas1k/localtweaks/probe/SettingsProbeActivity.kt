package io.github.fumtas1k.localtweaks.probe

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.fumtas1k.localtweaks.R

class SettingsProbeActivity : Activity() {
    private lateinit var permissionText: TextView
    private lateinit var valueText: TextView
    private lateinit var statusText: TextView
    private lateinit var setZeroButton: Button
    private lateinit var setOneButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    override fun onResume() {
        super.onResume()
        refreshState(updateStatus = false)
    }

    private fun createContentView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.probe_title)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.probe_warning)
            textSize = 16f
            setPadding(0, dp(16), 0, dp(16))
        })

        permissionText = TextView(this).apply { textSize = 18f }
        valueText = TextView(this).apply {
            textSize = 18f
            setPadding(0, dp(8), 0, dp(16))
        }
        statusText = TextView(this).apply {
            text = getString(R.string.status_idle)
            textSize = 16f
            setPadding(0, dp(16), 0, 0)
        }
        content.addView(permissionText)
        content.addView(valueText)

        content.addView(createButton(R.string.open_permission) { openWriteSettings() })
        content.addView(createButton(R.string.refresh) { refreshState(updateStatus = true) })

        setZeroButton = createButton(R.string.set_zero) { writeAndVerify(0) }
        setOneButton = createButton(R.string.set_one) { writeAndVerify(1) }
        content.addView(setZeroButton)
        content.addView(setOneButton)
        content.addView(statusText)

        return ScrollView(this).apply { addView(content) }
    }

    private fun createButton(labelResId: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = getString(labelResId)
            isAllCaps = false
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        }

    private fun refreshState(updateStatus: Boolean) {
        val canWrite = Settings.System.canWrite(this)
        permissionText.text = getString(
            if (canWrite) R.string.permission_granted else R.string.permission_denied,
        )
        valueText.text = getString(R.string.current_value, readRawValue())
        setZeroButton.isEnabled = canWrite
        setOneButton.isEnabled = canWrite
        if (updateStatus) {
            statusText.text = getString(R.string.status_refreshed)
        }
    }

    private fun openWriteSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            statusText.text = getString(R.string.status_open_settings_error)
        }
    }

    private fun writeAndVerify(expected: Int) {
        if (!Settings.System.canWrite(this)) {
            statusText.text = getString(R.string.status_permission_required)
            refreshState(updateStatus = false)
            return
        }

        try {
            val accepted = Settings.System.putInt(
                contentResolver,
                FORCED_SHUTTER_SOUND_KEY,
                expected,
            )
            val actual = readRawValue()
            statusText.text = when {
                !accepted -> getString(R.string.status_write_rejected, actual)
                actual == expected.toString() -> {
                    getString(R.string.status_write_success, expected, actual)
                }
                else -> getString(R.string.status_read_back_mismatch, expected, actual)
            }
        } catch (error: SecurityException) {
            statusText.text = getString(
                R.string.status_security_error,
                error.message.orEmpty(),
            )
        } catch (error: IllegalArgumentException) {
            statusText.text = getString(
                R.string.status_illegal_argument,
                error.message.orEmpty(),
            )
        }
        refreshState(updateStatus = false)
    }

    private fun readRawValue(): String =
        Settings.System.getString(contentResolver, FORCED_SHUTTER_SOUND_KEY) ?: "null"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val FORCED_SHUTTER_SOUND_KEY =
            "csc_pref_camera_forced_shuttersound_key"
    }
}
