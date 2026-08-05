package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class FileSystemVerifiedDownloadAccessTest {
    @Test
    fun copiesOnlyTheExpectedVerifiedArtifact() {
        val root = Files.createTempDirectory("verified-download-access").toFile()
        val bytes = "verified bytes".toByteArray()
        val digest = ModelDigest(sha256(bytes))
        publish(root, bytes, digest)
        val destination = File(root, "installation/model.gguf")

        val result =
            FileSystemVerifiedDownloadAccess(root).copyTo(
                VerifiedDownloadCopyRequest(
                    handle = VerifiedDownloadHandle(digest.sha256),
                    expectedDigest = digest,
                    expectedSizeBytes = bytes.size.toLong(),
                ),
                destination,
            )

        result as VerifiedDownloadCopyResult.Success
        assertEquals(digest, result.digest)
        assertEquals(bytes.size.toLong(), result.sizeBytes)
        assertTrue(destination.readBytes().contentEquals(bytes))
    }

    @Test
    fun rejectsHandleThatDoesNotMatchExpectedDigest() {
        val root = Files.createTempDirectory("verified-download-access").toFile()
        val bytes = "verified bytes".toByteArray()
        val digest = ModelDigest(sha256(bytes))
        publish(root, bytes, digest)
        val destination = File(root, "installation/model.gguf")

        val result =
            FileSystemVerifiedDownloadAccess(root).copyTo(
                VerifiedDownloadCopyRequest(
                    handle = VerifiedDownloadHandle("b".repeat(64)),
                    expectedDigest = digest,
                    expectedSizeBytes = bytes.size.toLong(),
                ),
                destination,
            )

        result as VerifiedDownloadCopyResult.Failure
        assertEquals(VerifiedDownloadAccessFailureCode.HANDLE_MISMATCH, result.code)
        assertFalse(destination.exists())
    }

    @Test
    fun rejectsVerifiedArtifactTamperedAfterPublication() {
        val root = Files.createTempDirectory("verified-download-access").toFile()
        val bytes = "verified bytes".toByteArray()
        val digest = ModelDigest(sha256(bytes))
        val published = publish(root, bytes, digest)
        published.writeBytes("tampered bytes!".toByteArray())
        val destination = File(root, "installation/model.gguf")

        val result =
            FileSystemVerifiedDownloadAccess(root).copyTo(
                VerifiedDownloadCopyRequest(
                    handle = VerifiedDownloadHandle(digest.sha256),
                    expectedDigest = digest,
                    expectedSizeBytes = published.length(),
                ),
                destination,
            )

        result as VerifiedDownloadCopyResult.Failure
        assertEquals(VerifiedDownloadAccessFailureCode.DIGEST_MISMATCH, result.code)
        assertFalse(destination.exists())
    }

    @Test
    fun discardsByOpaqueHandleWithinVerifiedRoot() {
        val root = Files.createTempDirectory("verified-download-access").toFile()
        val bytes = "verified bytes".toByteArray()
        val digest = ModelDigest(sha256(bytes))
        val published = publish(root, bytes, digest)

        val discarded =
            FileSystemVerifiedDownloadAccess(root).discard(VerifiedDownloadHandle(digest.sha256))

        assertTrue(discarded)
        assertFalse(published.exists())
    }

    private fun publish(root: File, bytes: ByteArray, digest: ModelDigest): File {
        val store = DownloadFileStore(root)
        val partial = store.createPartial().apply { writeBytes(bytes) }
        return store.publish(partial, digest, bytes.size.toLong()).file
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
