package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ModelSelectionVerificationTest {
    @Test
    fun `selection uses full verification instead of lookup verified hint`() {
        val digest = ModelDigest("1".repeat(64))
        var verifyCalls = 0
        val store = object : ModelStore {
            override fun find(digest: ModelDigest): StoredModel = StoredModel(
                digest = digest,
                file = File("model.gguf"),
                sizeBytes = 123L,
                verified = false,
            )

            override fun verify(digest: ModelDigest): VerificationResult {
                verifyCalls += 1
                return VerificationResult(
                    valid = true,
                    actualDigest = digest,
                    detail = "Stored model SHA-256 matches its content-addressed path",
                )
            }

            override fun import(source: File, artifact: GgufArtifact): StoredModel = error("unused")

            override fun remove(digest: ModelDigest): Boolean = error("unused")

            override fun snapshot(): ModelStoreSnapshot = error("unused")
        }

        verifyStoredModelForSelection(store, digest)

        assertEquals(1, verifyCalls)
    }
}
