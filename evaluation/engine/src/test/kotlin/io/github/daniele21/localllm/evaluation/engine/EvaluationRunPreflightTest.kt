package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EvaluationRunPreflightTest {
    @Test
    fun `valid supported run evaluates checks in deterministic order`() = runBlocking {
        val calls = mutableListOf<String>()
        val preflight = preflight(
            datasetCheck = { _, _ -> calls += "dataset"; null },
            evaluatorCheck = { _ -> calls += "evaluators"; null },
            profileCheck = { _, model ->
                calls += "profile"
                assertEquals("supported-model", model.profile.id)
                null
            },
        )

        val result = preflight.validate(config())

        assertTrue(result is EvaluationStepResult.Success)
        assertEquals(listOf("dataset", "evaluators", "profile"), calls)
    }

    @Test
    fun `unsupported model short circuits downstream checks`() = runBlocking {
        var downstreamCalled = false
        val preflight = EvaluationRunPreflight(
            modelResolver = ControlledEvaluationModelResolver(
                FixedSupportedEvaluationModelSource(emptyList()),
                FakeModelStore(null),
            ),
            datasetPreflight = EvaluationDatasetPreflight { _, _ -> downstreamCalled = true; null },
            evaluatorPreflight = EvaluationEvaluatorPreflight { _ -> downstreamCalled = true; null },
            executionProfilePreflight = EvaluationExecutionProfilePreflight { _, _ -> downstreamCalled = true; null },
        )

        val result = preflight.validate(config()) as EvaluationStepResult.Failure

        assertEquals(EvaluationFailureCode.MODEL_UNSUPPORTED, result.failure.code)
        assertTrue(!downstreamCalled)
    }

    @Test
    fun `first typed compatibility failure is returned without later checks`() = runBlocking {
        val failure = EvaluationFailure(
            stage = EvaluationFailureStage.PREFLIGHT,
            code = EvaluationFailureCode.DATASET_DIGEST_MISMATCH,
        )
        var evaluatorCalled = false
        var profileCalled = false
        val preflight = preflight(
            datasetCheck = { _, _ -> failure },
            evaluatorCheck = { _ -> evaluatorCalled = true; null },
            profileCheck = { _, _ -> profileCalled = true; null },
        )

        val result = preflight.validate(config()) as EvaluationStepResult.Failure

        assertSame(failure, result.failure)
        assertTrue(!evaluatorCalled)
        assertTrue(!profileCalled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non preflight failure from compatibility source is rejected`() = runBlocking {
        val invalid = EvaluationFailure(
            stage = EvaluationFailureStage.MODEL_PREPARATION,
            code = EvaluationFailureCode.RUNTIME_FAILURE,
        )
        preflight(datasetCheck = { _, _ -> invalid }).validate(config())
    }

    private fun preflight(
        datasetCheck: (EvaluationDatasetIdentity, SamplingSelection) -> EvaluationFailure? = { _, _ -> null },
        evaluatorCheck: (EvaluationDatasetIdentity) -> EvaluationFailure? = { _ -> null },
        profileCheck: (EvaluationExecutionProfileRef, ResolvedEvaluationModel) -> EvaluationFailure? = { _, _ -> null },
    ): EvaluationRunPreflight {
        val profile = profile()
        return EvaluationRunPreflight(
            modelResolver = ControlledEvaluationModelResolver(
                FixedSupportedEvaluationModelSource(listOf(profile)),
                FakeModelStore(StoredModel(profile.artifact.digest, File("model.gguf"), 1_024, verified = true)),
            ),
            datasetPreflight = EvaluationDatasetPreflight(datasetCheck),
            evaluatorPreflight = EvaluationEvaluatorPreflight(evaluatorCheck),
            executionProfilePreflight = EvaluationExecutionProfilePreflight(profileCheck),
        )
    }

    private fun config(): EvaluationRunConfig {
        val dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("fixture"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        )
        return EvaluationRunConfig(
            runId = EvaluationRunId("run-1"),
            model = EvaluationModelIdentity(
                artifactDigest = ModelDigest("a".repeat(64)),
                modelProfileId = "supported-model",
                quantization = "Q4_K_M",
            ),
            dataset = dataset,
            sampling = SamplingSelection.create(
                dataset = dataset,
                policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
                seed = 0,
                orderedCaseIds = listOf(EvaluationCaseId("case-a")),
            ),
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
    }

    private fun profile() = GgufModelProfile(
        id = "supported-model",
        artifact = GgufArtifact(
            digest = ModelDigest("a".repeat(64)),
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
