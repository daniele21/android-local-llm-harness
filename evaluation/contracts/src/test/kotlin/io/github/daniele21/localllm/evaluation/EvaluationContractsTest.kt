package io.github.daniele21.localllm.evaluation

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationContractsTest {
    @Test
    fun `sample selection is ordered unique and digest verified`() {
        val dataset = datasetIdentity()
        val policy = SamplingPolicyRef(SamplingPolicyId("stratified"), version = 1)
        val cases = listOf(EvaluationCaseId("case-a"), EvaluationCaseId("case-b"))

        val selection = SamplingSelection.create(dataset, policy, seed = 7L, orderedCaseIds = cases)

        assertEquals(cases, selection.orderedCaseIds)
        assertEquals(CanonicalEvaluationHasher.sampleSetDigest(cases), selection.digest)
        assertNotEquals(
            selection.digest,
            CanonicalEvaluationHasher.sampleSetDigest(cases.reversed()),
        )
    }

    @Test
    fun `evaluator outcomes preserve valid incorrect and invalid output semantics`() {
        val correct = EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT)
        val incorrect = EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INCORRECT)
        val invalid = EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INVALID_OUTPUT)

        assertEquals(1.0, correct.score.value, 0.0)
        assertEquals(0.0, incorrect.score.value, 0.0)
        assertNotEquals(incorrect.code, invalid.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `partial evaluator outcome rejects terminal scores`() {
        EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.PARTIAL)
    }

    @Test
    fun `compatibility contracts derive compatibility only from typed mismatches`() {
        val qualityCompatible = QualityCompatibility(emptySet())
        val runtimeIncompatible = RuntimeCompatibility(setOf(RuntimeMismatchReason.DEVICE_CLASS))

        assertTrue(qualityCompatible.compatible)
        assertFalse(runtimeIncompatible.compatible)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `run config rejects a sampling selection from another dataset`() {
        val first = datasetIdentity()
        val second = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("other"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("2".repeat(64)),
        )
        val sampling = SamplingSelection.create(
            dataset = second,
            policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
            seed = 0L,
            orderedCaseIds = listOf(EvaluationCaseId("case-1")),
        )

        EvaluationRunConfig(
            runId = EvaluationRunId("run-1"),
            model = modelIdentity(),
            dataset = first,
            sampling = sampling,
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
    }

    private fun datasetIdentity(): EvaluationDatasetIdentity = EvaluationDatasetIdentity(
        id = EvaluationDatasetId("general-purpose"),
        version = EvaluationDatasetVersion("1.0.0"),
        digest = EvaluationDatasetDigest("1".repeat(64)),
    )

    private fun modelIdentity(): EvaluationModelIdentity = EvaluationModelIdentity(
        artifactDigest = ModelDigest("a".repeat(64)),
        modelProfileId = "qwen35-08b-q4-k-m",
        tier = "0.8B",
        quantization = "Q4_K_M",
    )
}
