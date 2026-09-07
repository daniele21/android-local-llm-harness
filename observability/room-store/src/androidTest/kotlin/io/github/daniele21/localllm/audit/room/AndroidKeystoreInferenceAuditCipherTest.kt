package io.github.daniele21.localllm.audit.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daniele21.localllm.audit.InferenceAuditFailureCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreInferenceAuditCipherTest {
    @Test
    fun keystoreAesGcmRoundTripsAndRejectsTampering() {
        val alias = "harnex.audit.test.${UUID.randomUUID()}"
        try {
            val cipher = AndroidKeystoreInferenceAuditCipher(alias)
            val plaintext = "sensitive local inference content".toByteArray()

            val sealed = cipher.seal(plaintext)

            assertFalse(sealed.contentEquals(plaintext))
            assertArrayEquals(plaintext, cipher.open(sealed))

            val tampered = sealed.copyOf().also { it[it.lastIndex] = (it.last() xor 1) }
            val failure = assertThrows(InferenceAuditCipherException::class.java) {
                cipher.open(tampered)
            }
            assertEquals(InferenceAuditFailureCode.CORRUPT_CONTENT, failure.code)
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(alias)) deleteEntry(alias)
            }
        }
    }

    private infix fun Byte.xor(other: Int): Byte = (toInt() xor other).toByte()
}
