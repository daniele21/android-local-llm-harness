package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.TelemetryRepository

fun interface EvaluationTelemetryCorrelationPort {
    fun metrics(requestId: RequestId): EvaluationCaseMetrics
}

class TelemetryRepositoryEvaluationCorrelator(private val repository: TelemetryRepository) : EvaluationTelemetryCorrelationPort {
    override fun metrics(requestId: RequestId): EvaluationCaseMetrics {
        val run = repository.findRun(requestId) ?: return EvaluationCaseMetrics()
        return run.toEvaluationCaseMetrics()
    }
}

private fun GenerationRunRecord.toEvaluationCaseMetrics(): EvaluationCaseMetrics = EvaluationCaseMetrics(
    ttftMs = timeToFirstTokenMs,
    totalDurationMs = totalMs,
    promptTokens = inputTokens,
    completionTokens = outputTokens,
    prefillTokensPerSecond = prefillTokensPerSecond(),
    decodeTokensPerSecond = decodeTokensPerSecond,
    processPssBytes = null,
    availableMemoryBytes = null,
    thermalStatus = null,
)

private fun GenerationRunRecord.prefillTokensPerSecond(): Double? {
    val tokens = inputTokens ?: return null
    val durationMs = prefillMs ?: return null
    if (durationMs <= 0L) return null
    return tokens.toDouble() * MILLIS_PER_SECOND / durationMs.toDouble()
}

private const val MILLIS_PER_SECOND = 1_000.0
