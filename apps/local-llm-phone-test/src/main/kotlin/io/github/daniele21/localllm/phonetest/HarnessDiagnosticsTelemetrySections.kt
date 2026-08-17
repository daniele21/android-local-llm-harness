package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.TelemetryRepository
import java.time.Instant

internal class HarnessDiagnosticsTelemetrySections(
    private val repository: TelemetryRepository,
    selectedModel: ImportedPhoneModel?,
    private val freshResourceCaptureSucceeded: Boolean,
) {
    private val benchmarkState = HarnessBenchmarkSource(repository) { selectedModel }.snapshot()

    fun appendTo(builder: StringBuilder) {
        builder.appendRunsSection()
        builder.appendResourcesSection()
        builder.appendHealthSection()
        builder.appendBenchmarkReadinessSection()
        builder.appendBenchmarkBaselinesSection()
        builder.appendLogsSection()
    }

    private fun StringBuilder.appendRunsSection() {
        val runs = repository.recentRuns(RUN_LIMIT)
        appendLine("[generation-runs]")
        appendLine("count=${runs.size}")
        runs.forEachIndexed { index, run -> appendLine(HarnessDiagnosticsExport.renderRun(index, run)) }
        appendLine()
    }

    private fun StringBuilder.appendResourcesSection() {
        val resources = repository.recentResourceSnapshots(RESOURCE_LIMIT)
        appendLine("[resources]")
        appendLine("freshCapture=${if (freshResourceCaptureSucceeded) "captured" else "unavailable"}")
        appendLine("count=${resources.size}")
        resources.forEachIndexed { index, resource -> appendResource(index, resource) }
        appendLine()
    }

    private fun StringBuilder.appendHealthSection() {
        val health = repository.healthResults()
        appendLine("[health]")
        appendLine("count=${health.size}")
        health.forEachIndexed { index, result ->
            appendLine(
                "health[$index] id=${result.id.safeToken()} status=${result.status.name} " +
                    "durationMs=${result.durationMs} detail=${result.detail.safeValue()}",
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendBenchmarkReadinessSection() {
        appendLine("[benchmark-readiness]")
        appendLine("eligibleKeys=${benchmarkState.eligibleKeys}")
        appendLine("sourceError=${benchmarkState.sourceError.asSafeOrNone()}")
        benchmarkState.readiness.forEachIndexed { index, readiness ->
            appendLine(
                "readiness[$index] useCase=${readiness.useCase.safeToken()} loadKind=${readiness.loadKind.safeToken()} " +
                    "baselineSamples=${readiness.baselineSamples}/${readiness.baselineRequired} " +
                    "comparisonSamples=${readiness.comparisonSamples}/${readiness.comparisonRequired} " +
                    "baselineCaptured=${readiness.baselineCaptured} captureReady=${readiness.captureReady} " +
                    "comparisonReady=${readiness.comparisonReady} detail=${readiness.detail.safeValue()}",
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendBenchmarkBaselinesSection() {
        val baselines = repository.benchmarkBaselines()
        val history = repository.benchmarkBaselineHistory(BENCHMARK_HISTORY_LIMIT)
        appendLine("[benchmark-baselines]")
        appendLine("activeCount=${baselines.size}")
        baselines.forEachIndexed { index, baseline -> appendBaseline("baseline", index, baseline) }
        appendLine("historyCount=${history.size}")
        history.forEachIndexed { index, baseline -> appendBaseline("history", index, baseline) }
        appendLine()
    }

    private fun StringBuilder.appendLogsSection() {
        val logs = HarnessLogSource(repository).snapshot(limit = LOG_LIMIT)
        appendLine("[structured-logs]")
        appendLine("count=${logs.logs.size}")
        appendLine("sourceError=${logs.sourceError.asSafeOrNone()}")
        logs.logs.forEachIndexed { index, log ->
            append("log[$index] time=${Instant.ofEpochMilli(log.timestampEpochMs)}")
            append(" level=${log.level.safeToken()}")
            append(" component=${log.component.safeToken()}")
            append(" event=${log.event.safeToken()}")
            append(" request=${log.requestIdPrefix.safeToken()}")
            if (log.fields.isNotEmpty()) {
                append(" fields=")
                append(log.fields.joinToString(",") { "${it.name.safeToken()}=${it.value.safeValue()}" })
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendResource(index: Int, resource: ResourceSnapshot) {
        appendLine(
            "resource[$index] time=${Instant.ofEpochMilli(resource.timestampEpochMs)} " +
                "pssBytes=${resource.processPssBytes.orUnavailable()} " +
                "nativeHeapBytes=${resource.nativeHeapBytes.orUnavailable()} " +
                "javaHeapBytes=${resource.javaHeapUsedBytes.orUnavailable()} " +
                "availableMemoryBytes=${resource.availableMemoryBytes.orUnavailable()} " +
                "lowMemory=${resource.lowMemory.orUnavailable()} thermal=${resource.thermalStatus.name}",
        )
    }

    private fun StringBuilder.appendBaseline(prefix: String, index: Int, baseline: BenchmarkBaseline) {
        appendLine(
            "$prefix[$index] app=${baseline.key.applicationId.value.safeToken()} " +
                "useCase=${baseline.key.useCaseId.value.safeToken()} " +
                "model=${baseline.key.modelDigest.sha256.take(12)}… loadKind=${baseline.key.modelLoadKind.name} " +
                "execution=${baseline.key.executionIdentity.fingerprint.take(16)}… samples=${baseline.sampleCount} " +
                "captured=${Instant.ofEpochMilli(baseline.capturedAtEpochMs)} " +
                "medianTtftMs=${baseline.medianTimeToFirstTokenMs.asDecimalOrUnavailable()} " +
                "p95TtftMs=${baseline.p95TimeToFirstTokenMs.asDecimalOrUnavailable()} " +
                "medianTotalMs=${baseline.medianTotalMs.asDecimalOrUnavailable()} " +
                "p95TotalMs=${baseline.p95TotalMs.asDecimalOrUnavailable()} " +
                "medianDecodeTokPerSec=${baseline.medianDecodeTokensPerSecond.asDecimalOrUnavailable()}",
        )
    }

    private companion object {
        const val RUN_LIMIT = 200
        const val LOG_LIMIT = 250
        const val RESOURCE_LIMIT = 200
        const val BENCHMARK_HISTORY_LIMIT = 100
    }
}
