package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledEvaluationModelResolverTest {
    @Test
    fun `supported installed verified artifact resolves without app binding mutation`() {
        val profile = profile()
        val stored = StoredModel(profile.artifact.digest, File("model.gguf"), 1_024, verified = true)
        val resolver = ControlledEvaluationModelResolver(
            supportedModels = FixedSupportedEvaluationModelSource(listOf(profile)),
            modelStore = FakeModelStore(stored),
        )

        val result = resolver.resolve(identity()) as EvaluationModelResolution.Resolved

        assertEquals(profile, result.model.profile)
        assertEquals(stored, result.model.storedModel)
        assertEquals(identity(), result.model.identity)
    }

    @Test
    fun `unknown product profile is unsupported`() {
        val resolver = ControlledEvaluationModelResolver(
            supportedModels = FixedSupportedEvaluationModelSource(emptyList()),
            modelStore = FakeModelStore(null),
        )

        val rejected = resolver.resolve(identity()) as EvaluationModelResolution.Rejected

        assertEquals(EvaluationFailureCode.MODEL_UNSUPPORTED, rejected.failure.code)
    }

    @Test
    fun `profile digest mismatch is unsupported even when artifact is locally present`() {
        val differentProfile = profile(digest = ModelDigest("b".repeat(64)))
        val resolver = ControlledEvaluationModelResolver(
            supportedModels = FixedSupportedEvaluationModelSource(listOf(differentProfile)),
            modelStore = FakeModelStore(
                StoredModel(identity().artifactDigest, File("model.gguf"), 1_024, verified = true),
            ),
        )

        val rejected = resolver.resolve(identity()) as EvaluationModelResolution.Rejected

        assertEquals(EvaluationFailureCode.MODEL_UNSUPPORTED, rejected.failure.code)
    }

    @Test
    fun `missing or unverified local artifact is not installed`() {
        val profile = profile()
        val missing = ControlledEvaluationModelResolver(
            FixedSupportedEvaluationModelSource(listOf(profile)),
            FakeModelStore(null),
        ).resolve(identity()) as EvaluationModelResolution.Rejected
        val unverified = ControlledEvaluationModelResolver(
            FixedSupportedEvaluationModelSource(listOf(profile)),
            FakeModelStore(StoredModel(profile.artifact.digest, File("model.gguf"), 1_024, verified = false)),
        ).resolve(identity()) as EvaluationModelResolution.Rejected

        assertEquals(EvaluationFailureCode.MODEL_NOT_INSTALLED, missing.failure.code)
        assertEquals(EvaluationFailureCode.MODEL_NOT_INSTALLED, unverified.failure.code)
    }

    @Test
    fun `declared quantization mismatch is unsupported`() {
        val resolver = ControlledEvaluationModelResolver(
            FixedSupportedEvaluationModelSource(listOf(profile())),
            FakeModelStore(StoredModel(identity().artifactDigest, File("model.gguf"), 1_024, verified = true)),
        )

        val rejected = resolver.resolve(identity().copy(quantization = "Q8_0")) as EvaluationModelResolution.Rejected

        assertEquals(EvaluationFailureCode.MODEL_UNSUPPORTED, rejected.failure.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `supported source rejects duplicate product profile ids`() {
        FixedSupportedEvaluationModelSource(listOf(profile(), profile(digest = ModelDigest("b".repeat(64)))))
    }

    private fun identity() = EvaluationModelIdentity(
        artifactDigest = ModelDigest("a".repeat(64)),
        modelProfileId = "supported-model",
        tier = "test",
        quantization = "Q4_K_M",
    )

    private fun profile(digest: ModelDigest = ModelDigest("a".repeat(64))) = GgufModelProfile(
        id = "supported-model",
        artifact = GgufArtifact(
            digest = digest,
            fileName = "model.gguf",
            sizeBytes = 1_024,
            architecture = "qwen",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported("fixture"),
        ),
        contextSize = 2_048,
        batchSize = 128,
        microBatchSize = 64,
        cpuThreads = 4,
        batchThreads = 4,
        gpuLayers = 0,
    )

    private class FakeModelStore(private val stored: StoredModel?) : ModelStore {
        override fun find(digest: ModelDigest): StoredModel? = stored?.takeIf { it.digest == digest }

        override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

        override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(
            valid = stored?.verified == true,
            actualDigest = stored?.digest,
            detail = "fixture",
        )

        override fun remove(digest: ModelDigest): Boolean = false

        override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(
            modelCount = if (stored == null) 0 else 1,
            totalBytes = stored?.sizeBytes ?: 0,
            entries = listOfNotNull(stored),
        )
    }
}
