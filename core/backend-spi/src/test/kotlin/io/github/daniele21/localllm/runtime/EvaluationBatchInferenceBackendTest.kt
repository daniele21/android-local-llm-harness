package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluationBatchInferenceBackendTest {
    @Test
    fun `evaluation batch context keeps explicit bounded width`() {
        val configuration = BackendEvaluationBatchContextConfiguration(
            perSequenceContextSize = 2_048,
            maxSequences = 4,
        )

        assertEquals(2_048, configuration.perSequenceContextSize)
        assertEquals(4, configuration.maxSequences)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evaluation batch context rejects production-like single sequence width`() {
        BackendEvaluationBatchContextConfiguration(
            perSequenceContextSize = 2_048,
            maxSequences = 1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evaluation batch context rejects unbounded width`() {
        BackendEvaluationBatchContextConfiguration(
            perSequenceContextSize = 2_048,
            maxSequences = 5,
        )
    }

    @Test
    fun `evaluation batch result preserves ordered unique request identity`() {
        val result = BackendEvaluationBatchResult(
            listOf(
                caseResult("case-a"),
                caseResult("case-b"),
            ),
        )

        assertEquals(listOf("case-a", "case-b"), result.cases.map(BackendEvaluationBatchCaseResult::requestId))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evaluation batch result rejects duplicate request identity`() {
        BackendEvaluationBatchResult(
            listOf(
                caseResult("case-a"),
                caseResult("case-a"),
            ),
        )
    }

    private fun caseResult(requestId: String): BackendEvaluationBatchCaseResult = BackendEvaluationBatchCaseResult(
        requestId = requestId,
        output = "result",
        outcome = BackendGenerationOutcome.Completed(
            BackendGenerationMetrics(
                inputTokens = 10,
                outputTokens = 2,
                promptDurationMs = 4,
                generationDurationMs = 5,
            ),
        ),
    )
}
