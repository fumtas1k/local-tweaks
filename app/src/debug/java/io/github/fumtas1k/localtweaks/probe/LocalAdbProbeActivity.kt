package io.github.fumtas1k.localtweaks.probe

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.fumtas1k.localtweaks.R

class LocalAdbProbeActivity : Activity() {
    private lateinit var session: ProbeAdbSession
    private lateinit var pairingPort: EditText
    private lateinit var pairingCode: EditText
    private lateinit var connectionPort: EditText
    private lateinit var status: TextView
    private var uiAlive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = ProbeAdbSession.getInstance(applicationContext)
        setContentView(createView())
        if (savedInstanceState?.getBoolean(KEY_CONFIGURATION_STATE) == true) {
            pairingPort.setText(savedInstanceState.getString(KEY_PAIRING_PORT).orEmpty())
            connectionPort.setText(savedInstanceState.getString(KEY_CONNECTION_PORT).orEmpty())
        }
        uiAlive = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (isChangingConfigurations) {
            outState.putBoolean(KEY_CONFIGURATION_STATE, true)
            outState.putString(KEY_PAIRING_PORT, pairingPort.text.toString())
            outState.putString(KEY_CONNECTION_PORT, connectionPort.text.toString())
        }
        // Pairing code is intentionally never put into the instance state.
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        uiAlive = false
        if (!isChangingConfigurations) {
            // Teardown is queued on the same process-scoped session as all
            // pair/connect/stream operations.
            session.disconnect()
        }
        super.onDestroy()
    }

    private fun createView(): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.adb_probe_title)
            textSize = 22f
        })
        pairingPort = input(R.string.pairing_port_hint, false)
        pairingCode = input(R.string.pairing_code_hint, true)
        connectionPort = input(R.string.connection_port_hint, false)
        content.addView(pairingPort)
        content.addView(pairingCode)
        content.addView(button(R.string.pair_button) { pair() })
        content.addView(connectionPort)
        content.addView(button(R.string.connect_button) { connect() })
        content.addView(button(R.string.echo_button) { echoHello() })
        status = TextView(this).apply {
            text = getString(R.string.not_started)
            textSize = 16f
            setPadding(0, dp(16), 0, 0)
        }
        content.addView(status)
        return ScrollView(this).apply { addView(content) }
    }

    private fun pair() {
        // Clear secrets as soon as the action starts, even when the port is
        // malformed or the code fails validation.
        val code = pairingCode.text.toString()
        pairingCode.text.clear()
        val port = parsePort(pairingPort.text.toString())
            ?: return show(getString(R.string.invalid_pairing_port))
        if (!code.matches(Regex("[0-9]{6}"))) {
            return show(getString(R.string.invalid_pairing_code))
        }
        show(getString(R.string.pairing_progress))
        session.pair(port, code, ::complete)
    }

    private fun connect() {
        val port = parsePort(connectionPort.text.toString())
            ?: return show(getString(R.string.invalid_connection_port))
        show(getString(R.string.connecting_progress))
        session.connect(port, ::complete)
    }

    private fun echoHello() {
        show(getString(R.string.echo_progress))
        session.echo(::complete)
    }

    private fun complete(message: String) {
        if (uiAlive && !isFinishing && !isDestroyed) show(message)
    }

    private fun parsePort(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
        return value.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    private fun input(hintResId: Int, secret: Boolean) = EditText(this).apply {
        hint = getString(hintResId)
        // Ports are restored explicitly only across configuration changes;
        // pairing code must never enter Bundle or automatic view state.
        isSaveEnabled = false
        setSaveFromParentEnabled(false)
        inputType = if (secret) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_NUMBER
        }
    }

    private fun button(labelResId: Int, action: () -> Unit) = Button(this).apply {
        text = getString(labelResId)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun show(message: String) {
        status.text = message
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_CONFIGURATION_STATE = "probe_configuration_state"
        const val KEY_PAIRING_PORT = "probe_pairing_port"
        const val KEY_CONNECTION_PORT = "probe_connection_port"
    }
}
