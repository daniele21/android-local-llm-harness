package io.github.daniele21.localllm.evaluation

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistenceContractsTest {
    @Test
    fun `history query defaults are bounded`() {
        val query = EvaluationRunQuery()

        assertEquals(DEFAULT_EVALUATION_HISTORY_LIMIT, query.limit)
        assertEquals(emptySet<EvaluationRunState>(), query.states)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `history query rejects zero limit`() {
        EvaluationRunQuery(limit = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `history query rejects oversized limit`() {
        EvaluationRunQuery(limit = MAX_EVALUATION_HISTORY_LIMIT + 1)
    }

    @Test
    fun `retention policy may explicitly remove all terminal history`() {
        val policy = EvaluationRetentionPolicy(maxTerminalRuns = 0)

        assertEquals(0, policy.maxTerminalRuns)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `retention policy rejects zero age`() {
        EvaluationRetentionPolicy(maxAgeMs = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `retention result rejects duplicate deleted ids`() {
        val id = EvaluationRunId("run-1")
        EvaluationRetentionResult(
            deletedRunIds = listOf(id, id),
            retainedRunCount = 0,
        )
    }
}
