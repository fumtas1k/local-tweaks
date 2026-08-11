package io.github.fumtas1k.localtweaks.adb

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Process-scoped, serialized ADB pairing, connection, and fixed read session. */
internal class LocalAdbSession private constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val streamReadExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val manager: LocalAdbManager by lazy { LocalAdbManager(applicationContext) }
    private var connectedPort: Int? = null

    fun pair(port: Int, code: String, onComplete: (Result<Unit>) -> Unit) {
        submit(onComplete) {
            val result = runCatching { manager.pair(port, code) }
            if (manager.consumeCredentialResetNotice()) throw IOException("ADB credentials were reset")
            if (!result.getOrThrow()) throw IOException("pairing failed")
            Unit
        }
    }

    fun connect(port: Int, onComplete: (Result<Unit>) -> Unit) {
        submit(onComplete) {
            if (manager.isConnected) {
                if (connectedPort != port) throw IOException("already connected")
                return@submit Unit
            }
            val result = runCatching { manager.connect(port) }
            if (manager.consumeCredentialResetNotice()) throw IOException("ADB credentials were reset")
            if (!result.getOrThrow()) throw IOException("connection failed")
            connectedPort = port
            Unit
        }
    }

    fun read(onComplete: (Result<String>) -> Unit) {
        submit(onComplete) {
            manager.openForcedShutterReadStream().use { readLimited(it) }
        }
    }

    /** Queue teardown behind every transport operation. */
    fun disconnect() {
        executor.execute {
            runCatching { manager.disconnect() }
            connectedPort = null
        }
    }

    private fun <T> submit(onComplete: (Result<T>) -> Unit, operation: () -> T) {
        executor.execute {
            val result = runCatching(operation)
            mainHandler.post { onComplete(result) }
        }
    }

    private fun readLimited(stream: AdbStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MILLIS)
        var zeroReads = 0
        stream.use {
            stream.openInputStream().use { input ->
                while (output.size() <= MAX_OUTPUT_BYTES) {
                    val remainingNanos = deadline - System.nanoTime()
                    if (remainingNanos <= 0) throw ReadTimeoutException()
                    val requested = minOf(buffer.size, MAX_OUTPUT_BYTES + 1 - output.size())
                    val read = try {
                        readWithTimeout(stream, input, buffer, requested, remainingNanos)
                    } catch (exception: IOException) {
                        if (exception is ReadTimeoutException || !stream.isClosed) throw exception
                        break
                    }
                    if (read < 0) break
                    if (read == 0) {
                        if (++zeroReads >= MAX_ZERO_READS) throw ProtocolException("ADB stream made no progress")
                        continue
                    }
                    zeroReads = 0
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_OUTPUT_BYTES) {
                        throw ProtocolException("ADB stream response is too large")
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
            throw (exception.cause as? IOException) ?: IOException("ADB stream read failed", exception.cause)
        }
    }

    private class ReadTimeoutException(cause: Throwable? = null) : IOException("ADB stream read timed out", cause)
    internal class ProtocolException(message: String) : IOException(message)

    companion object {
        const val MAX_OUTPUT_BYTES = 128
        private const val MAX_ZERO_READS = 3
        private const val READ_TIMEOUT_MILLIS = 5_000L

        @Volatile private var instance: LocalAdbSession? = null

        fun getInstance(context: Context): LocalAdbSession = instance ?: synchronized(this) {
            instance ?: LocalAdbSession(context).also { instance = it }
        }
    }
}
