package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.TelemetryRepository

fun interface HealthClock {
    fun nowNanos(): Long
}

data class HealthAssessment(
    val status: HealthStatus,
    val detail: String,
)

interface HealthCheck {
    val id: String

    fun evaluate(): HealthAssessment
}

data class HealthSuiteReport(
    val results: List<HealthCheckResult>,
) {
    val status: HealthStatus = when {
        results.any { it.status == HealthStatus.FAIL } -> HealthStatus.FAIL
        results.any { it.status == HealthStatus.WARN } -> HealthStatus.WARN
        results.isEmpty() || results.all { it.status == HealthStatus.NOT_RUN } -> HealthStatus.NOT_RUN
        else -> HealthStatus.PASS
    }
}

class HealthEngine(
    checks: List<HealthCheck>,
    private val telemetryRepository: TelemetryRepository,
    private val clock: HealthClock = HealthClock(System::nanoTime),
) {
    private val checksById = checks.associateBy(HealthCheck::id).also { indexed ->
        require(indexed.size == checks.size) { "Health check IDs must be unique" }
        require(indexed.keys.none(String::isBlank)) { "Health check IDs must not be blank" }
    }

    fun availableChecks(): Set<String> = checksById.keys

    fun runAll(): HealthSuiteReport = run(checksById.keys)

    fun run(ids: Collection<String>): HealthSuiteReport {
        val results = ids.map { id ->
            val check = checksById[id]
            if (check == null) {
                HealthCheckResult(
                    id = id,
                    status = HealthStatus.NOT_RUN,
                    detail = "Health check is not registered",
                    durationMs = 0L,
                )
            } else {
                execute(check)
            }
        }
        results.forEach(telemetryRepository::saveHealth)
        return HealthSuiteReport(results)
    }

    private fun execute(check: HealthCheck): HealthCheckResult {
        val startedAt = clock.nowNanos()
        val assessment = try {
            check.evaluate()
        } catch (_: Throwable) {
            HealthAssessment(
                status = HealthStatus.FAIL,
                detail = "Health check failed unexpectedly",
            )
        }
        val durationMs = ((clock.nowNanos() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)
        return HealthCheckResult(
            id = check.id,
            status = assessment.status,
            detail = assessment.detail,
            durationMs = durationMs,
        )
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
