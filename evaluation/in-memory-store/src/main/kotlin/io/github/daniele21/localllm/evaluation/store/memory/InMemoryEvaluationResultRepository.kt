package io.github.daniele21.localllm.evaluation.store.memory

import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationResultRepository
import io.github.daniele21.localllm.evaluation.EvaluationRetentionPolicy
import io.github.daniele21.localllm.evaluation.EvaluationRetentionResult
import io.github.daniele21.localllm.evaluation.EvaluationRunDeleteStatus
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationRunQuery
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.EvaluationRunSummary
import io.github.daniele21.localllm.evaluation.PersistedEvaluationRun

class InMemoryEvaluationResultRepository(private val clock: () -> Long = System::currentTimeMillis) : EvaluationResultRepository {
    private val lock = Any()
    private val runs = linkedMapOf<EvaluationRunId, MutableStoredRun>()

    override suspend fun createRun(summary: EvaluationRunSummary) = synchronized(lock) {
        require(summary.runId !in runs) { "Evaluation run already exists" }
        runs[summary.runId] = MutableStoredRun(summary = summary)
    }

    override suspend fun updateRunSummary(summary: EvaluationRunSummary) = synchronized(lock) {
        val stored = requireRun(summary.runId)
        require(stored.summary.config == summary.config) { "Evaluation run config cannot change after creation" }
        requireValidTransition(stored.summary.state, summary.state)
        stored.summary = summary
    }

    override suspend fun appendCaseResult(runId: EvaluationRunId, result: EvaluationCaseResult) = synchronized(lock) {
        val stored = requireRun(runId)
        require(!stored.summary.state.isTerminal()) { "Cannot append case result to terminal evaluation run" }
        require(result.caseId in stored.summary.config.sampling.orderedCaseIds) {
            "Evaluation case result must belong to run sample set"
        }
        stored.caseResults[result.caseId] = result
    }

    override suspend fun getRun(runId: EvaluationRunId): PersistedEvaluationRun? = synchronized(lock) {
        runs[runId]?.snapshot()
    }

    override suspend fun queryRuns(query: EvaluationRunQuery): List<EvaluationRunSummary> = synchronized(lock) {
        val startedBeforeEpochMs = query.startedBeforeEpochMs
        runs.values.asSequence()
            .map { it.summary }
            .filter { query.states.isEmpty() || it.state in query.states }
            .filter { query.datasetId == null || it.config.dataset.id == query.datasetId }
            .filter { query.modelDigest == null || it.config.model.artifactDigest == query.modelDigest }
            .filter { startedBeforeEpochMs == null || it.startedAtEpochMs < startedBeforeEpochMs }
            .sortedWith(compareByDescending<EvaluationRunSummary> { it.startedAtEpochMs }.thenBy { it.runId.value })
            .take(query.limit)
            .toList()
    }

    override suspend fun deleteRun(runId: EvaluationRunId): EvaluationRunDeleteStatus = synchronized(lock) {
        val stored = runs[runId] ?: return@synchronized EvaluationRunDeleteStatus.NOT_FOUND
        if (!stored.summary.state.isTerminal()) {
            EvaluationRunDeleteStatus.ACTIVE_RUN
        } else {
            runs.remove(runId)
            EvaluationRunDeleteStatus.DELETED
        }
    }

    override suspend fun applyRetention(policy: EvaluationRetentionPolicy): EvaluationRetentionResult = synchronized(lock) {
        val now = clock()
        val maxAgeMs = policy.maxAgeMs
        val terminal = runs.values
            .filter { it.summary.state.isTerminal() }
            .sortedWith(compareByDescending<MutableStoredRun> { it.summary.startedAtEpochMs }.thenBy { it.summary.runId.value })

        val keepByCount = terminal.take(policy.maxTerminalRuns).map { it.summary.runId }.toSet()
        val expiredByAge = if (maxAgeMs == null) {
            emptySet()
        } else {
            terminal.filter { now - it.summary.startedAtEpochMs >= maxAgeMs }.map { it.summary.runId }.toSet()
        }
        val deleteIds = terminal.asSequence()
            .map { it.summary.runId }
            .filter { it !in keepByCount || it in expiredByAge }
            .toList()

        deleteIds.forEach(runs::remove)
        EvaluationRetentionResult(
            deletedRunIds = deleteIds,
            retainedRunCount = runs.size,
        )
    }

    private fun requireRun(runId: EvaluationRunId): MutableStoredRun = requireNotNull(runs[runId]) {
        "Evaluation run does not exist"
    }

    private fun requireValidTransition(from: EvaluationRunState, to: EvaluationRunState) {
        require(from == to || to in ALLOWED_TRANSITIONS.getValue(from)) {
            "Invalid evaluation run transition: $from -> $to"
        }
    }

    private data class MutableStoredRun(
        var summary: EvaluationRunSummary,
        val caseResults: LinkedHashMap<io.github.daniele21.localllm.evaluation.EvaluationCaseId, EvaluationCaseResult> = linkedMapOf(),
    ) {
        fun snapshot(): PersistedEvaluationRun = PersistedEvaluationRun(
            summary = summary,
            caseResults = summary.config.sampling.orderedCaseIds.mapNotNull(caseResults::get),
        )
    }

    private companion object {
        val ALLOWED_TRANSITIONS: Map<EvaluationRunState, Set<EvaluationRunState>> = mapOf(
            EvaluationRunState.CREATED to setOf(EvaluationRunState.VALIDATING, EvaluationRunState.CANCELLING, EvaluationRunState.FAILED),
            EvaluationRunState.VALIDATING to
                setOf(EvaluationRunState.PREPARING_MODEL, EvaluationRunState.CANCELLING, EvaluationRunState.FAILED),
            EvaluationRunState.PREPARING_MODEL to setOf(
                EvaluationRunState.WARMING_UP,
                EvaluationRunState.RUNNING,
                EvaluationRunState.CANCELLING,
                EvaluationRunState.FAILED,
            ),
            EvaluationRunState.WARMING_UP to setOf(EvaluationRunState.RUNNING, EvaluationRunState.CANCELLING, EvaluationRunState.FAILED),
            EvaluationRunState.RUNNING to setOf(EvaluationRunState.AGGREGATING, EvaluationRunState.CANCELLING, EvaluationRunState.FAILED),
            EvaluationRunState.AGGREGATING to setOf(EvaluationRunState.COMPLETED, EvaluationRunState.CANCELLING, EvaluationRunState.FAILED),
            EvaluationRunState.CANCELLING to setOf(EvaluationRunState.CANCELLED, EvaluationRunState.FAILED),
            EvaluationRunState.COMPLETED to emptySet(),
            EvaluationRunState.CANCELLED to emptySet(),
            EvaluationRunState.FAILED to emptySet(),
        )
    }
}

private fun EvaluationRunState.isTerminal(): Boolean = when (this) {
    EvaluationRunState.COMPLETED,
    EvaluationRunState.CANCELLED,
    EvaluationRunState.FAILED,
    -> true

    else -> false
}
