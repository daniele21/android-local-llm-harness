package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthControlPlane
import io.github.daniele21.localllm.observability.HealthFinding
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.HealthSuiteReport
import io.github.daniele21.localllm.observability.ModelIntegrityTarget
import io.github.daniele21.localllm.observability.SanityExecutor
import io.github.daniele21.localllm.observability.SanitySuiteDefinition
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.store.ModelStore

class HealthEngine(
    modelStore: ModelStore,
    private val telemetryRepository: TelemetryRepository,
    sanityExecutor: SanityExecutor,
    private val epochClock: () -> Long = System::currentTimeMillis,
    monotonicClock: () -> Long = System::nanoTime,
) : HealthControlPlane {
    private val modelIntegrityChecker = ModelIntegrityChecker(modelStore, monotonicClock)
    private val sanitySuiteRunner = SanitySuiteRunner(sanityExecutor, monotonicClock)

    override fun runModelIntegrity(target: ModelIntegrityTarget): HealthSuiteReport = completeReport(
        suiteId = "model-integrity:${target.id}",
        startedAt = epochClock(),
        findings = modelIntegrityChecker.run(target),
    )

    override fun runSanitySuite(definition: SanitySuiteDefinition): HealthSuiteReport = completeReport(
        suiteId = "sanity:${definition.id}",
        startedAt = epochClock(),
        findings = sanitySuiteRunner.run(definition),
    )

    override fun latestResults(): List<HealthCheckResult> = runCatching {
        telemetryRepository.healthResults()
    }.getOrDefault(emptyList())

    private fun completeReport(suiteId: String, startedAt: Long, findings: List<HealthFinding>): HealthSuiteReport {
        findings.forEach { finding -> persistFinding(suiteId, finding) }
        val completedAt = epochClock().coerceAtLeast(startedAt)
        return HealthSuiteReport(
            suiteId = suiteId,
            startedAtEpochMs = startedAt,
            completedAtEpochMs = completedAt,
            status = aggregateStatus(findings),
            findings = findings,
        )
    }

    private fun persistFinding(suiteId: String, finding: HealthFinding) {
        val persistedDetail = buildString {
            append(finding.detail)
            finding.remediation?.let { remediation ->
                append(" Remediation: ")
                append(remediation)
            }
        }
        runCatching {
            telemetryRepository.saveHealth(
                HealthCheckResult(
                    id = "$suiteId:${finding.id}",
                    status = finding.status,
                    detail = persistedDetail,
                    durationMs = finding.durationMs,
                ),
            )
        }
    }

    private fun aggregateStatus(findings: List<HealthFinding>): HealthStatus = when {
        findings.any { it.status == HealthStatus.FAIL } -> HealthStatus.FAIL
        findings.any { it.status == HealthStatus.WARN } -> HealthStatus.WARN
        findings.all { it.status == HealthStatus.NOT_RUN } -> HealthStatus.NOT_RUN
        findings.any { it.status == HealthStatus.NOT_RUN } -> HealthStatus.WARN
        else -> HealthStatus.PASS
    }
}
