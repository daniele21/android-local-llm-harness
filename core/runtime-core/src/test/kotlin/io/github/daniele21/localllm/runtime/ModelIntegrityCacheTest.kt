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
        mutate(file)
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

    @Test
    fun `verified atomic import is rehashed after its file stamp changes`() {
        val file = temporaryModel("abc")
        val digest = ModelDigest("e".repeat(64))
        val store = CountingModelStore(valid = true)
        val cache = ModelIntegrityCache()

        cache.verify(store, StoredModel(digest, file, file.length(), verified = true))
        mutate(file)
        cache.verify(store, StoredModel(digest, file, file.length(), verified = true))

        assertEquals(1, store.verificationCalls)
        file.delete()
    }

    @Test
    fun `health snapshot reports current cache entries as healthy`() {
        val file = temporaryModel("abc")
        val stored = StoredModel(ModelDigest("f".repeat(64)), file, file.length(), verified = true)
        val store = CountingModelStore(valid = true, entries = listOf(stored))
        val cache = ModelIntegrityCache()
        cache.verify(store, stored)

        val snapshot = cache.healthSnapshot(store)

        assertEquals(1, snapshot.entryCount)
        assertEquals(1, snapshot.healthyEntryCount)
        assertEquals(0, snapshot.staleEntryCount)
        assertEquals(0, snapshot.orphanedEntryCount)
        assertTrue(snapshot.healthy)
        file.delete()
    }

    @Test
    fun `health snapshot detects stale cached file stamps`() {
        val file = temporaryModel("abc")
        val stored = StoredModel(ModelDigest("1".repeat(64)), file, file.length(), verified = true)
        val store = CountingModelStore(valid = true, entries = listOf(stored))
        val cache = ModelIntegrityCache()
        cache.verify(store, stored)
        mutate(file)

        val snapshot = cache.healthSnapshot(store)

        assertEquals(1, snapshot.entryCount)
        assertEquals(1, snapshot.staleEntryCount)
        assertEquals(0, snapshot.orphanedEntryCount)
        assertFalse(snapshot.healthy)
        file.delete()
    }

    @Test
    fun `health snapshot detects entries whose model was removed`() {
        val file = temporaryModel("abc")
        val stored = StoredModel(ModelDigest("2".repeat(64)), file, file.length(), verified = true)
        val store = CountingModelStore(valid = true, entries = listOf(stored))
        val cache = ModelIntegrityCache()
        cache.verify(store, stored)
        store.entries = emptyList()
        val probe = ModelIntegrityCacheHealthProbe(cache, store)

        val snapshot = probe.snapshot()

        assertEquals("model-integrity", probe.id)
        assertEquals(1, snapshot.entryCount)
        assertEquals(0, snapshot.staleEntryCount)
        assertEquals(1, snapshot.orphanedEntryCount)
        assertFalse(snapshot.healthy)
        file.delete()
    }

    @Test
    fun `repair revalidates stale entries and removes orphaned entries`() {
        val staleFile = temporaryModel("stale")
        val orphanedFile = temporaryModel("orphaned")
        val stale = StoredModel(ModelDigest("3".repeat(64)), staleFile, staleFile.length(), verified = true)
        val orphaned = StoredModel(ModelDigest("4".repeat(64)), orphanedFile, orphanedFile.length(), verified = true)
        val store = CountingModelStore(valid = true, entries = listOf(stale, orphaned))
        val cache = ModelIntegrityCache()
        cache.verify(store, stale)
        cache.verify(store, orphaned)
        mutate(staleFile)
        store.entries = listOf(stale)

        val result = ModelIntegrityCacheMaintenanceControl(cache, store).repair()

        assertEquals(1, result.before.staleEntryCount)
        assertEquals(1, result.before.orphanedEntryCount)
        assertEquals(1, result.revalidatedEntryCount)
        assertEquals(1, result.removedEntryCount)
        assertEquals(0, result.failedEntryCount)
        assertEquals(1, result.after.entryCount)
        assertTrue(result.after.healthy)
        assertTrue(result.successful)
        assertEquals(1, store.verificationCalls)
        staleFile.delete()
        orphanedFile.delete()
    }

    @Test
    fun `repair removes stale cache entry when model verification fails`() {
        val file = temporaryModel("abc")
        val stored = StoredModel(ModelDigest("5".repeat(64)), file, file.length(), verified = true)
        val store = CountingModelStore(valid = false, entries = listOf(stored))
        val cache = ModelIntegrityCache()
        cache.verify(store, stored)
        mutate(file)

        val result = cache.repair(store)

        assertEquals(1, result.removedEntryCount)
        assertEquals(0, result.failedEntryCount)
        assertEquals(0, result.after.entryCount)
        assertTrue(result.after.healthy)
        file.delete()
    }

    @Test
    fun `repair preserves stale entry when verification throws`() {
        val file = temporaryModel("abc")
        val stored = StoredModel(ModelDigest("6".repeat(64)), file, file.length(), verified = true)
        val store = CountingModelStore(valid = true, entries = listOf(stored), throwOnVerify = true)
        val cache = ModelIntegrityCache()
        cache.verify(store, stored)
        mutate(file)

        val result = cache.repair(store)

        assertEquals(0, result.changedEntryCount)
        assertEquals(1, result.failedEntryCount)
        assertEquals(1, result.after.staleEntryCount)
        assertFalse(result.successful)
        file.delete()
    }

    private fun temporaryModel(content: String): File = File.createTempFile("integrity-cache", ".gguf").apply {
        writeText(content)
        deleteOnExit()
    }

    private fun mutate(file: File) {
        file.appendText("d")
        file.setLastModified(file.lastModified() + 1_000)
    }
}

private class CountingModelStore(
    private val valid: Boolean,
    entries: List<StoredModel> = emptyList(),
    private val throwOnVerify: Boolean = false,
) : ModelStore {
    var verificationCalls: Int = 0
    var entries: List<StoredModel> = entries

    override fun find(digest: ModelDigest): StoredModel? = entries.find { it.digest == digest }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult {
        verificationCalls += 1
        if (throwOnVerify) error("verification failed")
        return VerificationResult(
            valid = valid,
            actualDigest = if (valid) digest else null,
            detail = if (valid) "valid" else "invalid",
        )
    }

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(
        modelCount = entries.size,
        totalBytes = entries.sumOf(StoredModel::sizeBytes),
        entries = entries,
    )
}
