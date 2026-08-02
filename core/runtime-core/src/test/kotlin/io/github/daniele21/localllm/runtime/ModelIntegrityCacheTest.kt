package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelIntegrityCacheTest {
    @Test
    fun `matching file stamp reuses successful verification`() {
        val file = temporaryModel("abc")
        val digest = ModelDigest("a".repeat(64))
        val store = CountingModelStore(valid = true)
        val stored = StoredModel(digest, file, file.length(), verified = false)
        val cache = ModelIntegrityCache()

        assertTrue(cache.verify(store, stored).valid)
        assertTrue(cache.verify(store, stored).valid)
        assertEquals(1, store.verificationCalls)
        assertEquals(1, cache.size())
        file.delete()
    }

    @Test
    fun `file change invalidates cached verification stamp`() {
        val file = temporaryModel("abc")
        val digest = ModelDigest("b".repeat(64))
        val store = CountingModelStore(valid = true)
        val cache = ModelIntegrityCache()

        cache.verify(store, StoredModel(digest, file, file.length(), verified = false))
        file.appendText("d")
        file.setLastModified(file.lastModified() + 1_000)
        cache.verify(store, StoredModel(digest, file, file.length(), verified = false))

        assertEquals(2, store.verificationCalls)
        file.delete()
    }

    @Test
    fun `failed verification is never cached`() {
        val file = temporaryModel("abc")
        val digest = ModelDigest("c".repeat(64))
        val store = CountingModelStore(valid = false)
        val stored = StoredModel(digest, file, file.length(), verified = false)
        val cache = ModelIntegrityCache()

        assertFalse(cache.verify(store, stored).valid)
        assertFalse(cache.verify(store, stored).valid)
        assertEquals(2, store.verificationCalls)
        assertEquals(0, cache.size())
        file.delete()
    }

    @Test
    fun `verified atomic import seeds the cache without rehashing`() {
        val file = temporaryModel("abc")
        val digest = ModelDigest("d".repeat(64))
        val store = CountingModelStore(valid = true)
        val cache = ModelIntegrityCache()

        val result = cache.verify(
            store,
            StoredModel(digest, file, file.length(), verified = true),
        )

        assertTrue(result.valid)
        assertEquals(0, store.verificationCalls)
        assertEquals(1, cache.size())
        file.delete()
    }

    private fun temporaryModel(content: String): File = File.createTempFile("integrity-cache", ".gguf").apply {
        writeText(content)
        deleteOnExit()
    }
}

private class CountingModelStore(private val valid: Boolean) : ModelStore {
    var verificationCalls: Int = 0

    override fun find(digest: ModelDigest): StoredModel? = null

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult {
        verificationCalls += 1
        return VerificationResult(
            valid = valid,
            actualDigest = if (valid) digest else null,
            detail = if (valid) "valid" else "invalid",
        )
    }

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}
