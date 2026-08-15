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
    timeToFirstTokenMs = timeToFirstTokenMs,
    totalMs = totalMs,
    prefillMs = prefillMs,
    decodeMs = decodeMs,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    decodeTokensPerSecond = decodeTokensPerSecond,
    processPssBytes = null,
    availableMemoryBytes = null,
    thermalStatus = null,
)
