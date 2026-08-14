package io.github.daniele21.localllm.evaluation

import io.github.daniele21.localllm.contracts.RequestId

data class EvaluationRunConfig(
    val runId: EvaluationRunId,
    val model: EvaluationModelIdentity,
    val dataset: EvaluationDatasetIdentity,
    val sampling: SamplingSelection,
    val executionProfile: EvaluationExecutionProfileRef,
    val loadPolicy: EvaluationModelLoadPolicy,
    val warmupPolicy: EvaluationWarmupPolicy,
    val caseTimeoutMs: Long,
) {
    init {
        require(sampling.dataset == dataset) { "Sampling selection dataset must match run dataset" }
        require(caseTimeoutMs > 0) { "Case timeout must be positive" }
        require(caseTimeoutMs <= MAX_CASE_TIMEOUT_MS) { "Case timeout must not exceed $MAX_CASE_TIMEOUT_MS ms" }
    }
}

enum class EvaluationRunState {
    CREATED,
    VALIDATING,
    PREPARING_MODEL,
    WARMING_UP,
    RUNNING,
    AGGREGATING,
    CANCELLING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class EvaluationProgress(
    val totalCases: Int,
    val attemptedCases: Int,
    val completedCases: Int,
    val currentCaseId: EvaluationCaseId? = null,
) {
    init {
        require(totalCases > 0) { "Evaluation progress total must be positive" }
        require(attemptedCases in 0..totalCases) { "Attempted cases must be within total cases" }
        require(completedCases in 0..attemptedCases) { "Completed cases must be within attempted cases" }
    }
}

enum class EvaluationCaseStatus {
    SCORED,
    INVALID_OUTPUT,
    TIMEOUT,
    RUNTIME_FAILURE,
    CANCELLED,
}

data class EvaluationCaseMetrics(
    val timeToFirstTokenMs: Long? = null,
    val totalMs: Long? = null,
    val prefillMs: Long? = null,
    val decodeMs: Long? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val decodeTokensPerSecond: Double? = null,
    val processPssBytes: Long? = null,
    val availableMemoryBytes: Long? = null,
    val thermalStatus: String? = null,
) {
    init {
        listOf(timeToFirstTokenMs, totalMs, prefillMs, decodeMs, processPssBytes, availableMemoryBytes).forEach { value ->
            require(value == null || value >= 0) { "Evaluation metric values must not be negative" }
        }
        listOf(inputTokens, outputTokens).forEach { value ->
            require(value == null || value >= 0) { "Evaluation token counts must not be negative" }
        }
        require(decodeTokensPerSecond == null || (decodeTokensPerSecond.isFinite() && decodeTokensPerSecond >= 0.0)) {
            "Decode throughput must be finite and non-negative"
        }
        validateOptionalStableText(thermalStatus, "Thermal status", 64)
    }
}

data class EvaluationCaseResult(
    val caseId: EvaluationCaseId,
    val categoryId: EvaluationCategoryId,
    val evaluator: EvaluatorSpec,
    val status: EvaluationCaseStatus,
    val outcome: EvaluationOutcome?,
    val requestId: RequestId?,
    val metrics: EvaluationCaseMetrics = EvaluationCaseMetrics(),
    val failure: EvaluationFailure? = null,
) {
    init {
        when (status) {
            EvaluationCaseStatus.SCORED -> require(outcome != null && failure == null) {
                "Scored case result requires evaluator outcome and no failure"
            }

            EvaluationCaseStatus.INVALID_OUTPUT -> require(
                outcome != null &&
                    failure == null &&
                    (outcome.code == EvaluatorOutcomeCode.INVALID_OUTPUT || outcome.code == EvaluatorOutcomeCode.AMBIGUOUS_OUTPUT),
            ) { "Invalid-output case result requires an invalid or ambiguous evaluator outcome without runtime failure" }

            EvaluationCaseStatus.TIMEOUT,
            EvaluationCaseStatus.RUNTIME_FAILURE,
            EvaluationCaseStatus.CANCELLED,
            -> require(outcome == null && failure != null) {
                "Non-evaluated case result requires a typed failure and no evaluator outcome"
            }
        }
    }
}

data class EvaluationCategoryScore(
    val categoryId: EvaluationCategoryId,
    val score: NormalizedScore,
    val scoredCaseCount: Int,
    val weight: Double? = null,
) {
    init {
        require(scoredCaseCount > 0) { "Category scored-case count must be positive" }
        require(weight == null || (weight.isFinite() && weight > 0.0)) { "Category weight must be finite and positive" }
    }
}

data class EvaluationQualitySummary(val aggregateScore: NormalizedScore?, val categoryScores: List<EvaluationCategoryScore>)

data class EvaluationReliabilitySummary(
    val totalCases: Int,
    val completedAndScored: Int,
    val incorrectButValid: Int,
    val invalidOutput: Int,
    val timeout: Int,
    val runtimeFailure: Int,
    val cancelled: Int,
    val skipped: Int,
) {
    init {
        require(totalCases > 0) { "Reliability total must be positive" }
        val counts = listOf(completedAndScored, incorrectButValid, invalidOutput, timeout, runtimeFailure, cancelled, skipped)
        require(counts.all { it >= 0 }) { "Reliability counts must not be negative" }
        require(counts.all { it <= totalCases }) { "Reliability counts must not exceed total cases" }
        require(incorrectButValid <= completedAndScored) { "Incorrect-but-valid count must be within scored cases" }
    }
}

data class EvaluationRunSummary(
    val runId: EvaluationRunId,
    val config: EvaluationRunConfig,
    val identity: EvaluationRunIdentity?,
    val state: EvaluationRunState,
    val progress: EvaluationProgress,
    val quality: EvaluationQualitySummary?,
    val reliability: EvaluationReliabilitySummary?,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val failure: EvaluationFailure?,
) {
    init {
        require(runId == config.runId) { "Run summary ID must match run config" }
        require(startedAtEpochMs >= 0) { "Run start timestamp must not be negative" }
        require(completedAtEpochMs == null || completedAtEpochMs >= startedAtEpochMs) {
            "Run completion timestamp must not precede start"
        }
        require(progress.totalCases == config.sampling.orderedCaseIds.size) {
            "Run progress total must match sampling selection"
        }
        identity?.let { validateIdentityMatchesConfig(it, config) }
        if (state == EvaluationRunState.COMPLETED) {
            require(identity != null && completedAtEpochMs != null && failure == null) {
                "Completed run summary requires identity and completion timestamp without failure"
            }
        }
        if (state == EvaluationRunState.FAILED) {
            require(failure != null && completedAtEpochMs != null) { "Failed run summary requires typed failure and completion timestamp" }
        }
        if (state == EvaluationRunState.CANCELLED) {
            require(completedAtEpochMs != null) { "Cancelled run summary requires completion timestamp" }
        }
    }
}

private fun validateIdentityMatchesConfig(identity: EvaluationRunIdentity, config: EvaluationRunConfig) {
    require(identity.model == config.model) { "Run identity model must match run config" }
    require(identity.dataset == config.dataset) { "Run identity dataset must match run config" }
    require(identity.sampleSetDigest == config.sampling.digest) { "Run identity sample set must match run config" }
    require(identity.samplingPolicy == config.sampling.policy) { "Run identity sampling policy must match run config" }
    require(identity.samplingSeed == config.sampling.seed) { "Run identity sampling seed must match run config" }
    require(identity.semanticExecution.execution.profile == config.executionProfile) {
        "Run identity execution profile must match run config"
    }
    require(identity.runtimeEnvironment.loadPolicy == config.loadPolicy) { "Run identity load policy must match run config" }
    require(identity.runtimeEnvironment.warmupPolicy == config.warmupPolicy) { "Run identity warm-up policy must match run config" }
}

private const val MAX_CASE_TIMEOUT_MS = 10 * 60 * 1_000L
