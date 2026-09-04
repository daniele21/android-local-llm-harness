package io.github.daniele21.localllm.audit.room

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.daniele21.localllm.audit.InferenceAuditFailureCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface InferenceAuditCipher {
    fun seal(plaintext: ByteArray): ByteArray

    fun open(ciphertext: ByteArray): ByteArray
}

internal class InferenceAuditCipherException(val code: InferenceAuditFailureCode, cause: Throwable? = null) :
    RuntimeException("Inference audit cryptographic operation failed", cause)

/** Encrypts audit content with an app-scoped Android Keystore AES-GCM key. */
internal class AndroidKeystoreInferenceAuditCipher(private val keyAlias: String = DEFAULT_KEY_ALIAS) : InferenceAuditCipher {
    init {
        require(keyAlias.isNotBlank()) { "Audit key alias must not be blank" }
    }

    override fun seal(plaintext: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)
        encodeEnvelope(cipher.iv, encrypted)
    } catch (error: GeneralSecurityException) {
        throw InferenceAuditCipherException(InferenceAuditFailureCode.ENCRYPTION_UNAVAILABLE, error)
    } catch (error: RuntimeException) {
        if (error is InferenceAuditCipherException) throw error
        throw InferenceAuditCipherException(InferenceAuditFailureCode.ENCRYPTION_UNAVAILABLE, error)
    }

    override fun open(ciphertext: ByteArray): ByteArray {
        val envelope = try {
            decodeEnvelope(ciphertext)
        } catch (error: RuntimeException) {
            throw InferenceAuditCipherException(InferenceAuditFailureCode.CORRUPT_CONTENT, error)
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, envelope.iv))
            cipher.doFinal(envelope.ciphertext)
        } catch (error: AEADBadTagException) {
            throw InferenceAuditCipherException(InferenceAuditFailureCode.CORRUPT_CONTENT, error)
        } catch (error: GeneralSecurityException) {
            throw InferenceAuditCipherException(InferenceAuditFailureCode.ENCRYPTION_UNAVAILABLE, error)
        } catch (error: RuntimeException) {
            if (error is InferenceAuditCipherException) throw error
            throw InferenceAuditCipherException(InferenceAuditFailureCode.ENCRYPTION_UNAVAILABLE, error)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encodeEnvelope(iv: ByteArray, encrypted: ByteArray): ByteArray {
        require(iv.size in 1..MAX_IV_BYTES) { "Invalid audit encryption IV" }
        require(encrypted.size <= MAX_CIPHERTEXT_BYTES) { "Encrypted audit content exceeds storage bound" }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(ENVELOPE_MAGIC)
                output.writeInt(ENVELOPE_VERSION)
                output.writeInt(iv.size)
                output.write(iv)
                output.writeInt(encrypted.size)
                output.write(encrypted)
            }
            bytes.toByteArray()
        }
    }

    private fun decodeEnvelope(value: ByteArray): Envelope = DataInputStream(ByteArrayInputStream(value)).use { input ->
        require(input.readInt() == ENVELOPE_MAGIC) { "Invalid audit encryption envelope" }
        require(input.readInt() == ENVELOPE_VERSION) { "Unsupported audit encryption envelope" }
        val ivSize = input.readInt()
        require(ivSize in 1..MAX_IV_BYTES) { "Invalid audit encryption IV size" }
        val iv = ByteArray(ivSize).also(input::readFully)
        val ciphertextSize = input.readInt()
        require(ciphertextSize in 1..MAX_CIPHERTEXT_BYTES) { "Invalid audit ciphertext size" }
        val encrypted = ByteArray(ciphertextSize).also(input::readFully)
        require(input.read() == -1) { "Unexpected trailing audit ciphertext data" }
        Envelope(iv, encrypted)
    }

    private data class Envelope(val iv: ByteArray, val ciphertext: ByteArray)

    private companion object {
        const val DEFAULT_KEY_ALIAS = "harnex.inference.audit.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val ENVELOPE_MAGIC = 0x48415831
        const val ENVELOPE_VERSION = 1
        const val MAX_IV_BYTES = 32
        const val MAX_CIPHERTEXT_BYTES = 1_048_576
    }
}
