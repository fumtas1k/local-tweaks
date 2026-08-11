package io.github.fumtas1k.localtweaks.adb

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The only production ADB transport. Its endpoint is always numeric loopback. */
internal class LocalAdbManager(private val context: Context) : AbsAdbConnectionManager() {
    private val sessionLock = Any()
    private val credentialLock = Any()
    private var credentials: Credentials? = null
    private var keyStore: KeyStore? = null
    @Volatile private var credentialResetPending = false
    @Volatile private var processRestartRequired = false

    init {
        setApi(Build.VERSION.SDK_INT)
        setHostAddress(LOOPBACK_HOST)
        setTimeout(10, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey(): PrivateKey = loadCredentials().keyPair.private
    override fun getCertificate(): Certificate = loadCredentials().certificate
    override fun getDeviceName(): String = "Local Tweaks"

    override fun pair(port: Int, pairingCode: String): Boolean = synchronized(sessionLock) {
        checkNotRestarted()
        super.pair(port, pairingCode)
    }

    override fun connect(port: Int): Boolean = synchronized(sessionLock) {
        checkNotRestarted()
        super.connect(port)
    }

    /** Opens only the fixed settings read command; no caller-supplied command is accepted. */
    fun openForcedShutterReadStream(): AdbStream = synchronized(sessionLock) {
        checkNotRestarted()
        super.openStream(FORCED_SHUTTER_READ_COMMAND)
    }

    fun openForcedShutterSetZeroStream(): AdbStream = synchronized(sessionLock) {
        checkNotRestarted()
        super.openStream(FORCED_SHUTTER_SET_ZERO_COMMAND)
    }

    fun openForcedShutterSetOneStream(): AdbStream = synchronized(sessionLock) {
        checkNotRestarted()
        super.openStream(FORCED_SHUTTER_SET_ONE_COMMAND)
    }

    override fun disconnect() = synchronized(sessionLock) { super.disconnect() }

    /** Deletes only this app's ADB identity; a process restart is required afterward. */
    fun resetCredentials() = synchronized(sessionLock) {
        runCatching { super.disconnect() }
        synchronized(credentialLock) {
            credentials = null
            credentialResetPending = false
            try {
                deleteStoredCredentials()
            } finally {
                // libadb can retain TLS identity state for this process.
                processRestartRequired = true
            }
        }
    }

    fun consumeCredentialResetNotice(): Boolean = synchronized(credentialLock) {
        val reset = credentialResetPending
        credentialResetPending = false
        reset
    }

    /**
     * Whether this app already has locally stored ADB credential material.
     *
     * Credentials are created lazily on the first pairing/connection attempt (see
     * [loadCredentials]), not only after a successful pairing. A `true` result here does not
     * mean pairing has ever succeeded; treat it only as a hint (e.g. for a default UI state),
     * never as proof of a working pairing.
     */
    fun hasStoredCredentials(): Boolean = synchronized(credentialLock) { hasStoredCredentialFiles() }

    private fun checkNotRestarted() {
        if (processRestartRequired) throw AdbRestartRequiredException()
    }

    private fun loadCredentials(): Credentials {
        checkNotRestarted()
        synchronized(credentialLock) {
            credentials?.let { return it }
            val hadStoredCredentials = hasStoredCredentialFiles()
            return try {
                loadExistingCredentials()?.also { credentials = it }
                    ?: createAndStoreAdbCredentials().also { credentials = it }
            } catch (exception: Exception) {
                if (!hadStoredCredentials) throw exception
                val cleanupFailure = runCatching { deleteStoredCredentials() }.exceptionOrNull()
                credentialResetPending = true
                processRestartRequired = true
                throw AdbRestartRequiredException().also {
                    it.addSuppressed(exception)
                    cleanupFailure?.let(it::addSuppressed)
                }
            }
        }
    }

    private fun hasStoredCredentialFiles(): Boolean =
        context.getFileStreamPath(ENCRYPTED_KEY_FILE).exists() ||
            context.getFileStreamPath(CERTIFICATE_FILE).exists() ||
            context.getFileStreamPath(ENCRYPTED_KEY_TEMP_FILE).exists() ||
            context.getFileStreamPath(CERTIFICATE_TEMP_FILE).exists() ||
            wrappingKeyAliasExists()

    private fun wrappingKeyAliasExists(): Boolean = try {
        getKeyStore().containsAlias(WRAPPING_KEY_ALIAS)
    } catch (_: Exception) {
        true
    }

    private fun loadExistingCredentials(): Credentials? {
        val encryptedFile = context.getFileStreamPath(ENCRYPTED_KEY_FILE)
        val certificateFile = context.getFileStreamPath(CERTIFICATE_FILE)
        if (!encryptedFile.exists() && !certificateFile.exists()) return null
        if (!encryptedFile.exists() || !certificateFile.exists()) {
            throw IOException("incomplete ADB credential files")
        }
        if (encryptedFile.length() !in 1..MAX_FILE_SIZE ||
            certificateFile.length() !in 1..MAX_CERTIFICATE_SIZE
        ) throw IOException("ADB credential file is too large")

        ensureWrappingKey()
        val privateKeyBytes = DataInputStream(context.openFileInput(ENCRYPTED_KEY_FILE)).use { input ->
            val iv = ByteArray(input.readInt().also { require(it in 12..32) })
            input.readFully(iv)
            val encrypted = ByteArray(input.readInt().also { require(it in 1..MAX_KEY_BLOB_SIZE) })
            input.readFully(encrypted)
            try {
                decryptPrivateKey(iv, encrypted)
            } finally {
                iv.fill(0)
                encrypted.fill(0)
            }
        }
        return try {
            val privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            Credentials(privateKey, loadCertificate())
        } finally {
            privateKeyBytes.fill(0)
        }
    }

    private fun createAndStoreAdbCredentials(): Credentials {
        ensureWrappingKey()
        val keyPair = KeyPairGenerator.getInstance("RSA").run {
            initialize(2048)
            generateKeyPair()
        }
        val certificate = createCertificate(keyPair)
        val encodedPrivateKey = requireNotNull(keyPair.private.encoded)
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
            val encrypted = cipher.doFinal(encodedPrivateKey)
            val iv = cipher.iv
            try {
                writeAtomically(ENCRYPTED_KEY_FILE) { output ->
                    DataOutputStream(output).use {
                        it.writeInt(iv.size)
                        it.write(iv)
                        it.writeInt(encrypted.size)
                        it.write(encrypted)
                    }
                }
                writeAtomically(CERTIFICATE_FILE) { output -> output.write(certificate.encoded) }
            } finally {
                iv.fill(0)
                encrypted.fill(0)
            }
        } finally {
            encodedPrivateKey.fill(0)
        }
        return Credentials(keyPair.private, certificate)
    }

    private fun writeAtomically(fileName: String, write: (java.io.FileOutputStream) -> Unit) {
        val target = context.getFileStreamPath(fileName)
        val temporary = File(target.parentFile, "$fileName.tmp")
        try {
            context.openFileOutput(temporary.name, Context.MODE_PRIVATE).use(write)
            check(temporary.renameTo(target)) { "could not commit $fileName" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun createCertificate(keyPair: KeyPair): Certificate {
        val subject = X500Name("CN=Local Tweaks")
        val serial = BigInteger(128, SecureRandom()).let { if (it.signum() == 0) BigInteger.ONE else it }
        val builder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            Date(System.currentTimeMillis() - ONE_MINUTE_MILLIS),
            Date(System.currentTimeMillis() + TEN_YEARS_MILLIS),
            subject,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer)).also {
            it.verify(keyPair.public)
        }
    }

    private fun loadCertificate(): Certificate = context.openFileInput(CERTIFICATE_FILE).use {
        CertificateFactory.getInstance("X.509").generateCertificate(it)
    }

    private fun decryptPrivateKey(iv: ByteArray, encrypted: ByteArray): ByteArray =
        Cipher.getInstance(AES_TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
            doFinal(encrypted)
        }

    private fun ensureWrappingKey() {
        val store = getKeyStore()
        if (store.containsAlias(WRAPPING_KEY_ALIAS)) return
        val spec = KeyGenParameterSpec.Builder(
            WRAPPING_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(spec)
            generateKey()
        }
    }

    private fun wrappingKey(): SecretKey =
        requireNotNull(getKeyStore().getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)

    private fun getKeyStore(): KeyStore = keyStore ?: KeyStore.getInstance(KEYSTORE_PROVIDER).also {
        it.load(null)
        keyStore = it
    }

    private fun deleteStoredCredentials() {
        var failure: Exception? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: Exception) {
                failure = failure?.also { it.addSuppressed(error) } ?: error
            }
        }
        attempt { deleteCredentialFile(ENCRYPTED_KEY_FILE) }
        attempt { deleteCredentialFile(CERTIFICATE_FILE) }
        attempt { deleteCredentialFile(ENCRYPTED_KEY_TEMP_FILE) }
        attempt { deleteCredentialFile(CERTIFICATE_TEMP_FILE) }
        attempt {
            try {
                val store = getKeyStore()
                if (store.containsAlias(WRAPPING_KEY_ALIAS)) store.deleteEntry(WRAPPING_KEY_ALIAS)
            } finally {
                keyStore = null
            }
        }
        failure?.let { error ->
            throw IOException("could not fully delete ADB credentials").also { it.addSuppressed(error) }
        }
    }

    private fun deleteCredentialFile(fileName: String) {
        val file = context.getFileStreamPath(fileName)
        if (file.exists() && !context.deleteFile(fileName)) {
            throw IOException("could not delete ADB credential file")
        }
    }

    private data class Credentials(val keyPair: KeyPair, val certificate: Certificate) {
        constructor(privateKey: PrivateKey, certificate: Certificate) : this(
            KeyPair(certificate.publicKey, privateKey), certificate,
        )
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val WRAPPING_KEY_ALIAS = "local_tweaks_adb_key_wrapping"
        const val ENCRYPTED_KEY_FILE = "adb_private_key.enc"
        const val CERTIFICATE_FILE = "adb_certificate.der"
        const val ENCRYPTED_KEY_TEMP_FILE = "$ENCRYPTED_KEY_FILE.tmp"
        const val CERTIFICATE_TEMP_FILE = "$CERTIFICATE_FILE.tmp"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val LOOPBACK_HOST = "127.0.0.1"
        const val FORCED_SHUTTER_READ_COMMAND =
            "shell:settings get system csc_pref_camera_forced_shuttersound_key"
        const val FORCED_SHUTTER_SET_ZERO_COMMAND =
            "shell:settings put system csc_pref_camera_forced_shuttersound_key 0"
        const val FORCED_SHUTTER_SET_ONE_COMMAND =
            "shell:settings put system csc_pref_camera_forced_shuttersound_key 1"
        const val MAX_KEY_BLOB_SIZE = 16 * 1024
        const val MAX_FILE_SIZE = MAX_KEY_BLOB_SIZE + 128
        const val MAX_CERTIFICATE_SIZE = 32 * 1024L
        const val ONE_MINUTE_MILLIS = 60_000L
        const val TEN_YEARS_MILLIS = 10L * 365 * 24 * 60 * 60 * 1000
    }
}
