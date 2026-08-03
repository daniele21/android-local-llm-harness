package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ModelIntegrityHealthCheckTest {
    @Test
    fun `warns when no models are installed`() {
        val result = ModelIntegrityHealthCheck(FakeModelStore(emptyList())).evaluate()

        assertEquals(HealthStatus.WARN, result.status)
    }

    @Test
    fun `passes when every installed model verifies`() {
        val models = listOf(stored("a"), stored("b"))
        val result = ModelIntegrityHealthCheck(FakeModelStore(models)).evaluate()

        assertEquals(HealthStatus.PASS, result.status)
        assertEquals("Verified 2 installed model artifact(s)", result.detail)
    }

    @Test
    fun `fails without exposing model paths when verification fails`() {
        val valid = stored("a")
        val invalid = stored("b")
        val result = ModelIntegrityHealthCheck(
            FakeModelStore(listOf(valid, invalid), invalidDigests = setOf(invalid.digest)),
        ).evaluate()

        assertEquals(HealthStatus.FAIL, result.status)
        assertEquals("1 of 2 installed model artifact(s) failed integrity verification", result.detail)
    }

    private fun stored(value: String): StoredModel {
        val digest = ModelDigest(value.repeat(64))
        return StoredModel(digest, File("/private/$value/model.gguf"), 10L, verified = false)
    }

    private class FakeModelStore(private val models: List<StoredModel>, private val invalidDigests: Set<ModelDigest> = emptySet()) :
        ModelStore {
        override fun find(digest: ModelDigest): StoredModel? = models.find { it.digest == digest }

        override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not needed")

        override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(
            valid = digest !in invalidDigests,
            actualDigest = digest,
            detail = "ignored",
        )

        override fun remove(digest: ModelDigest): Boolean = false

        override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(
            modelCount = models.size,
            totalBytes = models.sumOf(StoredModel::sizeBytes),
            entries = models,
        )
    }
}
