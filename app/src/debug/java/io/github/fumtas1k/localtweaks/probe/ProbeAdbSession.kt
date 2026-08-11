package io.github.fumtas1k.localtweaks.probe

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.fumtas1k.localtweaks.R
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Process-scoped, serialized Local ADB session shared by probe Activities. */
internal class ProbeAdbSession private constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    // Session operations are serial; one read worker is sufficient. A timeout
    // closes the stream before cancelling its blocked read.
    private val streamReadExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val manager: ProbeAdbManager by lazy { ProbeAdbManager(applicationContext) }
    private var connectedPort: Int? = null

    fun pair(port: Int, code: String, onComplete: (String) -> Unit) {
        submit(onComplete) {
            val result = runCatching { manager.pair(port, code) }
            val recovered = manager.consumeCredentialResetNotice()
            when {
                recovered -> applicationContext.getString(R.string.credentials_reset_restart)
                result.getOrThrow() -> applicationContext.getString(R.string.pairing_success)
                else -> applicationContext.getString(R.string.pairing_failed)
            }
        }
    }

    fun connect(port: Int, onComplete: (String) -> Unit) {
        submit(onComplete) {
            // The process-scoped transport survives Activity recreation;
            // reconnecting an already-established libadb session returns
            // false, so make this operation idempotent.
            if (manager.isConnected) {
                when (val establishedPort = connectedPort) {
                    port -> applicationContext.getString(
                        R.string.connection_already_same_port,
                        port,
                    )
                    null -> applicationContext.getString(R.string.connection_already_unknown_port)
                    else -> applicationContext.getString(
                        R.string.connection_already_other_port,
                        establishedPort,
                    )
                }
            } else {
                val result = runCatching { manager.connect(port) }
                val recovered = manager.consumeCredentialResetNotice()
                if (recovered) {
                    applicationContext.getString(R.string.credentials_reset_restart)
                } else if (result.getOrThrow()) {
                    connectedPort = port
                    applicationContext.getString(R.string.connection_success)
                } else {
                    applicationContext.getString(R.string.connection_failed)
                }
            }
        }
    }

    fun echo(onComplete: (String) -> Unit) {
        submit(onComplete) {
            val response = manager.openStream("shell:echo hello").use { readLimited(it) }.trim()
            if (response != EXPECTED_ECHO_RESPONSE) {
                throw IOException(applicationContext.getString(R.string.unexpected_echo_response, response))
            }
            response
        }
    }

    /** Queue teardown behind every currently running transport operation. */
    fun disconnect() {
        executor.execute {
            try {
                manager.disconnect()
            } catch (_: Exception) {
                // Teardown is best effort; the process-scoped session is
                // discarded with the process if libadb cannot close cleanly.
            } finally {
                connectedPort = null
            }
        }
    }

    private fun <T> submit(onComplete: (String) -> Unit, operation: () -> T) {
        executor.execute {
            val result = runCatching { operation().toString() }.fold(
                onSuccess = { it },
                onFailure = {
                    applicationContext.getString(
                        R.string.operation_failed,
                        it.javaClass.simpleName,
                        it.message.orEmpty(),
                    )
                },
            )
            mainHandler.post {
                onComplete(result)
            }
        }
    }

    private fun readLimited(stream: AdbStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MILLIS)
        var zeroReads = 0
        stream.use {
            stream.openInputStream().use { input ->
                while (output.size() <= MAX_OUTPUT_SIZE) {
                    val remainingNanos = deadline - System.nanoTime()
                    if (remainingNanos <= 0) throw IOException("ADB stream read timed out")
                    val requested = minOf(buffer.size, MAX_OUTPUT_SIZE + 1 - output.size())
                    val read = try {
                        readWithTimeout(stream, input, buffer, requested, remainingNanos)
                    } catch (exception: IOException) {
                        // A normal remote close can surface as an IOException
                        // in libadb. The strict command response check below
                        // still determines whether the operation succeeded.
                        if (exception is ReadTimeoutException || !stream.isClosed) throw exception
                        // Remote close after the response has been drained.
                        // echo() still requires the exact expected response.
                        break
                    }
                    if (read < 0) break
                    if (read == 0) {
                        if (++zeroReads >= MAX_ZERO_READS) {
                            throw IOException("ADB stream made no progress")
                        }
                        continue
                    }
                    zeroReads = 0
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_OUTPUT_SIZE) {
                        throw IOException("ADB stream response is too large")
                    }
                }
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun readWithTimeout(
        stream: AdbStream,
        input: java.io.InputStream,
        buffer: ByteArray,
        length: Int,
        remainingNanos: Long,
    ): Int {
        val read = streamReadExecutor.submit(Callable { input.read(buffer, 0, length) })
        return try {
            read.get(remainingNanos, TimeUnit.NANOSECONDS)
        } catch (exception: TimeoutException) {
            runCatching { stream.close() }
            read.cancel(true)
            throw ReadTimeoutException(exception)
        } catch (exception: InterruptedException) {
            read.cancel(true)
            Thread.currentThread().interrupt()
            throw IOException("ADB stream read interrupted", exception)
        } catch (exception: java.util.concurrent.ExecutionException) {
            throw (exception.cause as? IOException)
                ?: IOException("ADB stream read failed", exception.cause)
        }
    }

    private class ReadTimeoutException(cause: Throwable) : IOException(
        "ADB stream read timed out",
        cause,
    )

    companion object {
        const val EXPECTED_ECHO_RESPONSE = "hello"
        const val MAX_OUTPUT_SIZE = 1024
        const val MAX_ZERO_READS = 3
        const val READ_TIMEOUT_MILLIS = 5_000L

        @Volatile private var instance: ProbeAdbSession? = null

        private fun get(context: Context): ProbeAdbSession = instance ?: synchronized(this) {
            instance ?: ProbeAdbSession(context).also { instance = it }
        }

        internal fun getInstance(context: Context): ProbeAdbSession = get(context)
    }
}
