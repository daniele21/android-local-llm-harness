package io.github.daniele21.localllm.evaluation

import io.github.daniele21.localllm.contracts.ModelDigest

data class EvaluationRunQuery(
    val states: Set<EvaluationRunState> = emptySet(),
    val datasetId: EvaluationDatasetId? = null,
    val modelDigest: ModelDigest? = null,
    val startedBeforeEpochMs: Long? = null,
    val limit: Int = DEFAULT_EVALUATION_HISTORY_LIMIT,
) {
    init {
        require(startedBeforeEpochMs == null || startedBeforeEpochMs >= 0) {
            "History timestamp boundary must not be negative"
        }
        require(limit in 1..MAX_EVALUATION_HISTORY_LIMIT) {
            "Evaluation history limit must be in 1..$MAX_EVALUATION_HISTORY_LIMIT"
        }
    }
}

data class EvaluationRetentionPolicy(
    val maxTerminalRuns: Int = DEFAULT_MAX_TERMINAL_EVALUATION_RUNS,
    val maxAgeMs: Long? = null,
) {
    init {
        require(maxTerminalRuns >= 0) { "Evaluation retention run limit must not be negative" }
        require(maxAgeMs == null || maxAgeMs > 0) {
            "Evaluation retention age must be positive when declared"
        }
    }
}

data class PersistedEvaluationRun(
    val summary: EvaluationRunSummary,
    val caseResults: List<EvaluationCaseResult>,
) {
    init {
        require(caseResults.map { it.caseId }.distinct().size == caseResults.size) {
            "Persisted evaluation case IDs must be unique per run"
        }
        require(caseResults.all { it.caseId in summary.config.sampling.orderedCaseIds }) {
            "Persisted evaluation case result must belong to the run sample set"
        }
    }
}

enum class EvaluationRunDeleteStatus {
    DELETED,
    NOT_FOUND,
    ACTIVE_RUN,
}

data class EvaluationRetentionResult(
    val deletedRunIds: List<EvaluationRunId>,
    val retainedRunCount: Int,
) {
    init {
        require(deletedRunIds.distinct().size == deletedRunIds.size) {
            "Retention result deleted run IDs must be unique"
        }
        require(retainedRunCount >= 0) { "Retention result retained run count must not be negative" }
    }
}

interface EvaluationResultRepository {
    suspend fun createRun(summary: EvaluationRunSummary)

    suspend fun updateRunSummary(summary: EvaluationRunSummary)

    suspend fun appendCaseResult(runId: EvaluationRunId, result: EvaluationCaseResult)

    suspend fun getRun(runId: EvaluationRunId): PersistedEvaluationRun?

    suspend fun queryRuns(query: EvaluationRunQuery = EvaluationRunQuery()): List<EvaluationRunSummary>

    suspend fun deleteRun(runId: EvaluationRunId): EvaluationRunDeleteStatus

    suspend fun applyRetention(policy: EvaluationRetentionPolicy): EvaluationRetentionResult
}

const val DEFAULT_EVALUATION_HISTORY_LIMIT = 50
const val MAX_EVALUATION_HISTORY_LIMIT = 500
const val DEFAULT_MAX_TERMINAL_EVALUATION_RUNS = 200
