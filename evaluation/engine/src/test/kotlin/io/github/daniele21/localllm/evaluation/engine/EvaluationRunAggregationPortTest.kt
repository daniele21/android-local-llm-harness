package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationRunAggregationPortTest {
    @Test
    fun `canonical port aggregates against dataset supplied categories`() {
        val config = config()
        val port = CanonicalEvaluationRunAggregationPort(
            categorySource = EvaluationDatasetCategorySource {
                listOf(EvaluationDatasetCategoryDefinition(EvaluationCategoryId("general"), "General"))
            },
        )

        val result = port.aggregate(config, listOf(scored(config.sampling.orderedCaseIds.single())))

        assertTrue(result is EvaluationStepResult.Success)
        val aggregation = (result as EvaluationStepResult.Success).value
        assertEquals(1.0, aggregation.quality.aggregateScore!!.value, 0.0)
        assertEquals(1, aggregation.reliability.completedAndScored)
    }

    @Test
    fun `missing category source fails closed`() {
        val result = CanonicalEvaluationRunAggregationPort(EvaluationDatasetCategorySource { null })
            .aggregate(config(), emptyList())

        assertTrue(result is EvaluationStepResult.Failure)
        assertEquals(EvaluationFailureCode.DATASET_NOT_FOUND, (result as EvaluationStepResult.Failure).failure.code)
    }

    @Test
    fun `invalid aggregation shape fails closed`() {
        val config = config()
        val foreign = scored(EvaluationCaseId("foreign"))
        val port = CanonicalEvaluationRunAggregationPort(
            EvaluationDatasetCategorySource {
                listOf(EvaluationDatasetCategoryDefinition(EvaluationCategoryId("general"), "General"))
            },
        )

        val result = port.aggregate(config, listOf(foreign))

        assertTrue(result is EvaluationStepResult.Failure)
        assertEquals(EvaluationFailureCode.INVALID_CONFIGURATION, (result as EvaluationStepResult.Failure).failure.code)
    }

    private fun scored(caseId: EvaluationCaseId) = EvaluationCaseResult(
        caseId = caseId,
        categoryId = EvaluationCategoryId("general"),
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        status = EvaluationCaseStatus.SCORED,
        outcome = EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT),
        requestId = null,
    )

    private fun config(): EvaluationRunConfig {
        val dataset = EvaluationDatasetIdentity(
            EvaluationDatasetId("fixture"),
            EvaluationDatasetVersion("1"),
            EvaluationDatasetDigest("1".repeat(64)),
        )
        return EvaluationRunConfig(
            runId = EvaluationRunId("run-aggregation"),
            model = EvaluationModelIdentity(ModelDigest("a".repeat(64)), "supported-model"),
            dataset = dataset,
            sampling = SamplingSelection.create(
                dataset = dataset,
                policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
                seed = 0,
                orderedCaseIds = listOf(EvaluationCaseId("case-a")),
            ),
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("profile"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
    }
}
