package io.github.daniele21.localllm.store

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileSystemModelStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `known content is imported under its SHA-256 path`() {
        val root = temporaryFolder.newFolder("store")
        val source = writeSource("source.gguf", ABC_BYTES)
        val store = FileSystemModelStore(root, bufferSizeBytes = 2)

        val stored = store.import(source, artifact(ABC_DIGEST, ABC_BYTES.size.toLong()))

        assertEquals(ModelDigest(ABC_DIGEST), stored.digest)
        assertEquals(
            File(root, "models/sha256/ba/$ABC_DIGEST/model.gguf").canonicalFile,
            stored.file.canonicalFile,
        )
        assertEquals(ABC_BYTES.size.toLong(), stored.sizeBytes)
        assertTrue(stored.verified)
        assertArrayEquals(ABC_BYTES, stored.file.readBytes())
        assertTrue(store.verify(ModelDigest(ABC_DIGEST)).valid)
    }

    @Test
    fun `same content is deduplicated without creating another object`() {
        val root = temporaryFolder.newFolder("store")
        val firstSource = writeSource("first.gguf", ABC_BYTES)
        val secondSource = writeSource("second.gguf", ABC_BYTES)
        val store = FileSystemModelStore(root)

        val first = store.import(firstSource, artifact(ABC_DIGEST, ABC_BYTES.size.toLong()))
        val modifiedAt = first.file.lastModified()
        val second = store.import(secondSource, artifact(ABC_DIGEST.uppercase(), ABC_BYTES.size.toLong()))

        assertEquals(first.file.canonicalFile, second.file.canonicalFile)
        assertEquals(modifiedAt, second.file.lastModified())
        assertEquals(1, store.snapshot().modelCount)
        assertTrue(second.verified)
    }

    @Test
    fun `digest mismatch rejects import and removes staging data`() {
        val root = temporaryFolder.newFolder("store")
        val source = writeSource("source.gguf", ABC_BYTES)
        val store = FileSystemModelStore(root)

        val error = assertThrows(ModelImportException::class.java) {
            store.import(source, artifact(ZERO_DIGEST, ABC_BYTES.size.toLong()))
        }

        assertEquals(ModelImportErrorCode.DIGEST_MISMATCH, error.code)
        assertNull(store.find(ModelDigest(ZERO_DIGEST)))
        assertStagingEmpty(root)
    }

    @Test
    fun `size mismatch rejects import before publication`() {
        val root = temporaryFolder.newFolder("store")
        val source = writeSource("source.gguf", ABC_BYTES)
        val store = FileSystemModelStore(root)

        val error = assertThrows(ModelImportException::class.java) {
            store.import(source, artifact(ABC_DIGEST, ABC_BYTES.size + 1L))
        }

        assertEquals(ModelImportErrorCode.SIZE_MISMATCH, error.code)
        assertNull(store.find(ModelDigest(ABC_DIGEST)))
        assertStagingEmpty(root)
    }

    @Test
    fun `invalid source and digest return structured import errors`() {
        val root = temporaryFolder.newFolder("store")
        val store = FileSystemModelStore(root)
        val missing = File(root, "missing.gguf")

        val sourceError = assertThrows(ModelImportException::class.java) {
            store.import(missing, artifact(ABC_DIGEST, ABC_BYTES.size.toLong()))
        }
        assertEquals(ModelImportErrorCode.INVALID_SOURCE, sourceError.code)

        val source = writeSource("source.gguf", ABC_BYTES)
        val digestError = assertThrows(ModelImportException::class.java) {
            store.import(source, artifact("not-a-sha256", ABC_BYTES.size.toLong()))
        }
        assertEquals(ModelImportErrorCode.INVALID_DIGEST, digestError.code)
    }

    @Test
    fun `corrupt existing object is never overwritten`() {
        val root = temporaryFolder.newFolder("store")
        val source = writeSource("source.gguf", ABC_BYTES)
        val destination = File(root, ModelStoreLayout.relativeArtifactPath(ModelDigest(ABC_DIGEST)))
        destination.parentFile.mkdirs()
        destination.writeBytes(byteArrayOf('x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte()))
        val original = destination.readBytes()
        val store = FileSystemModelStore(root)

        val error = assertThrows(ModelImportException::class.java) {
            store.import(source, artifact(ABC_DIGEST, ABC_BYTES.size.toLong()))
        }

        assertEquals(ModelImportErrorCode.DESTINATION_CONFLICT, error.code)
        assertArrayEquals(original, destination.readBytes())
        assertStagingEmpty(root)
    }

    @Test
    fun `verification detects mutation after a successful import`() {
        val root = temporaryFolder.newFolder("store")
        val source = writeSource("source.gguf", ABC_BYTES)
        val store = FileSystemModelStore(root)
        val stored = store.import(source, artifact(ABC_DIGEST, ABC_BYTES.size.toLong()))

        stored.file.writeText("tampered")
        val verification = store.verify(ModelDigest(ABC_DIGEST))

        assertFalse(verification.valid)
        assertNotNull(verification.actualDigest)
        assertTrue(verification.detail.contains("does not match"))
    }

    @Test
    fun `snapshot find and remove expose store state without rehashing`() {
        val root = temporaryFolder.newFolder("store")
        val source = writeSource("source.gguf", ABC_BYTES)
        val store = FileSystemModelStore(root)
        val stored = store.import(source, artifact(ABC_DIGEST, ABC_BYTES.size.toLong()))

        val found = store.find(ModelDigest(ABC_DIGEST.uppercase()))
        val snapshot = store.snapshot()

        assertNotNull(found)
        assertFalse(found!!.verified)
        assertEquals(stored.file.canonicalFile, found.file.canonicalFile)
        assertEquals(1, snapshot.modelCount)
        assertEquals(ABC_BYTES.size.toLong(), snapshot.totalBytes)
        assertEquals(ModelDigest(ABC_DIGEST), snapshot.entries.single().digest)
        assertFalse(snapshot.entries.single().verified)

        assertTrue(store.remove(ModelDigest(ABC_DIGEST)))
        assertFalse(store.remove(ModelDigest(ABC_DIGEST)))
        assertNull(store.find(ModelDigest(ABC_DIGEST)))
        assertEquals(0, store.snapshot().modelCount)
    }

    @Test
    fun `layout requires a canonical SHA-256 value`() {
        assertEquals(
            "models/sha256/ba/$ABC_DIGEST/model.gguf",
            ModelStoreLayout.relativeArtifactPath(ModelDigest(ABC_DIGEST.uppercase())),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ModelStoreLayout.relativeArtifactPath(ModelDigest("abc"))
        }
    }

    private fun writeSource(name: String, bytes: ByteArray): File =
        temporaryFolder.newFile(name).apply { writeBytes(bytes) }

    private fun artifact(digest: String, sizeBytes: Long): GgufArtifact = GgufArtifact(
        digest = ModelDigest(digest),
        fileName = "fixture.gguf",
        sizeBytes = sizeBytes,
        architecture = "qwen2",
        quantization = "Q4_K_M",
        source = ArtifactSource.Imported("unit-test"),
    )

    private fun assertStagingEmpty(root: File) {
        val staging = File(root, "models/.staging")
        assertTrue(!staging.exists() || staging.listFiles().orEmpty().isEmpty())
    }

    private companion object {
        val ABC_BYTES = "abc".encodeToByteArray()
        const val ABC_DIGEST = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        const val ZERO_DIGEST = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
