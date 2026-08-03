package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Suppress("TooManyFunctions")
class ConsolePresenter(zoneId: ZoneId = ZoneId.systemDefault()) {
    private val timestampFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
        .withZone(zoneId)

    fun present(tab: ConsoleTab, snapshot: ConsoleSnapshot): ConsoleScreen = when (tab) {
        ConsoleTab.OVERVIEW -> overview(snapshot)
        ConsoleTab.RUNS -> runs(snapshot)
        ConsoleTab.LOGS -> logs(snapshot)
        ConsoleTab.HEALTH -> health(snapshot)
        ConsoleTab.RESOURCES -> resources(snapshot)
        ConsoleTab.BENCHMARKS -> benchmarks(snapshot)
    }

    fun presentRequestDetail(detail: ConsoleRequestDetail): ConsoleScreen {
        detail.sourceError?.let { error ->
            return ConsoleScreen(
                title = "Request ${shortId(detail.requestId.value)}",
                subtitle = "Request-correlated telemetry unavailable",
                cards = listOf(
                    ConsoleCard(
                        title = "Telemetry source",
                        lines = listOf(error),
                        emphasis = ConsoleEmphasis.NEGATIVE,
                    ),
                ),
            )
        }

        val cards = mutableListOf<ConsoleCard>()
        detail.run?.let { run -> cards += runCard(run, "Request metrics", openRequest = false) }
            ?: run { cards += emptyCard("Run record not found", "Request metrics") }

        val orderedTimeline = detail.timeline.sortedBy { it.timestampEpochMs }
        if (orderedTimeline.isEmpty()) {
            cards += emptyCard("No correlated structured logs recorded", "Request timeline")
        } else {
            val originEpochMs = detail.run?.startedAtEpochMs ?: orderedTimeline.first().timestampEpochMs
            cards += ConsoleCard(
                title = "Timeline summary",
                lines = listOf(
                    "Events: ${orderedTimeline.size}",
                    "First event: ${formatTimestamp(orderedTimeline.first().timestampEpochMs)}",
                    "Last event: ${formatTimestamp(orderedTimeline.last().timestampEpochMs)}",
                    "Observed span: ${formatOffset(orderedTimeline.last().timestampEpochMs - originEpochMs)}",
                ),
            )
            cards += orderedTimeline.mapIndexed { index, log ->
                timelineCard(
                    log = log,
                    originEpochMs = originEpochMs,
                    sequence = index + 1,
                    total = orderedTimeline.size,
                )
            }
        }

        return ConsoleScreen(
            title = "Request ${shortId(detail.requestId.value)}",
            subtitle = "Chronological, privacy-safe request timeline",
            cards = cards,
        )
    }

    private fun overview(snapshot: ConsoleSnapshot): ConsoleScreen {
        val cards = mutableListOf<ConsoleCard>()
        snapshot.sourceError?.let { error ->
            cards += ConsoleCard(
                title = "Telemetry source",
                lines = listOf(error),
                emphasis = ConsoleEmphasis.NEGATIVE,
            )
        }
        cards += ConsoleCard(
            title = "Runtime",
            lines = listOf(
                "Status: ${snapshot.runtime.status}",
                "Backend: ${snapshot.runtime.backend}",
                "Loaded model: ${snapshot.runtime.loadedModel}",
                "Active sessions: ${snapshot.runtime.activeSessions ?: "Unavailable"}",
                "Queue depth: ${snapshot.runtime.queueDepth ?: "Unavailable"}",
                "Source: ${snapshot.runtime.source}",
            ),
            emphasis = if (snapshot.runtime.activeSessions == null) {
                ConsoleEmphasis.WARNING
            } else {
                ConsoleEmphasis.NEUTRAL
            },
        )
        cards += ConsoleCard(
            title = "Telemetry",
            lines = listOf(
                "Runs: ${snapshot.runs.size}",
                "Logs: ${snapshot.logs.size}",
                "Health checks: ${snapshot.health.size}",
                "Resource snapshots: ${snapshot.resources.size}",
                "Benchmark baselines: ${snapshot.benchmarkBaselines.size}",
                "Captured: ${formatTimestamp(snapshot.capturedAtEpochMs)}",
            ),
        )
        snapshot.runs.firstOrNull()?.let { run -> cards += runCard(run, "Latest request") }
        cards += healthSummaryCard(snapshot.health)
        snapshot.resources.firstOrNull()?.let { resource ->
            cards += resourceCard(resource, "Latest resource snapshot")
        }
        return ConsoleScreen(
            title = "Overview",
            subtitle = "Read-only runtime and observability summary",
            cards = cards,
        )
    }

    private fun runs(snapshot: ConsoleSnapshot): ConsoleScreen = ConsoleScreen(
        title = "Generation runs",
        subtitle = "Tap a request for its metrics and correlated timeline",
        cards = snapshot.runs.map { runCard(it) }.ifEmpty {
            listOf(emptyCard("No generation runs recorded"))
        },
    )

    private fun logs(snapshot: ConsoleSnapshot): ConsoleScreen = ConsoleScreen(
        title = "Structured logs",
        subtitle = "Request-correlated events with privacy-safe structured fields",
        cards = snapshot.logs.map { logCard(it) }.ifEmpty {
            listOf(emptyCard("No structured logs recorded"))
        },
    )

    private fun health(snapshot: ConsoleSnapshot): ConsoleScreen = ConsoleScreen(
        title = "Health and sanity",
        subtitle = "Persisted results; execution controls remain a separate slice",
        cards = snapshot.health
            .sortedWith(compareBy<HealthCheckResult> { healthRank(it.status) }.thenBy { it.id })
            .map { healthCard(it) }
            .ifEmpty { listOf(emptyCard("No health checks recorded")) },
    )

    private fun resources(snapshot: ConsoleSnapshot): ConsoleScreen = ConsoleScreen(
        title = "Memory and thermal",
        subtitle = "Raw snapshots; charts and sampling controls remain a separate slice",
        cards = snapshot.resources.map { resourceCard(it) }.ifEmpty {
            listOf(emptyCard("No resource snapshots recorded"))
        },
    )

    private fun benchmarks(snapshot: ConsoleSnapshot): ConsoleScreen = ConsoleScreen(
        title = "Benchmark baselines",
        subtitle = "Current active baseline for each app, use case, model and load class",
        cards = snapshot.benchmarkBaselines.map { benchmarkCard(it) }.ifEmpty {
            listOf(emptyCard("No benchmark baselines recorded"))
        },
    )

    private fun runCard(
        run: GenerationRunRecord,
        title: String? = null,
        openRequest: Boolean = true,
    ): ConsoleCard = ConsoleCard(
        title = title ?: "${run.status.name} · ${shortId(run.requestId.value)}",
        lines = listOf(
            "Request: ${run.requestId.value}",
            "Application: ${run.applicationId.value}",
            "Use case: ${run.useCaseId.value}",
            "Model: ${shortDigest(run.modelDigest.sha256)}",
            "Started: ${formatTimestamp(run.startedAtEpochMs)}",
            "Completed: ${run.completedAtEpochMs?.let(::formatTimestamp) ?: "In progress"}",
            "Load class: ${run.modelLoadKind.name}",
            "Queue: ${formatDuration(run.queueMs)} · Load: ${formatDuration(run.modelLoadMs)}",
            "TTFT: ${formatDuration(run.timeToFirstTokenMs)} · Total: ${formatDuration(run.totalMs)}",
            "Prefill: ${formatDuration(run.prefillMs)} · Decode: ${formatDuration(run.decodeMs)}",
            "Tokens: ${run.inputTokens ?: "?"} in / ${run.outputTokens ?: "?"} out",
            "Decode speed: ${formatRate(run.decodeTokensPerSecond)}",
            "Error code: ${run.errorCode ?: "None"}",
        ),
        emphasis = run.status.toEmphasis(),
        openRequestId = run.requestId.takeIf { openRequest },
    )

    private fun logCard(log: StructuredLog): ConsoleCard = ConsoleCard(
        title = "${log.level.name} · ${log.event}",
        lines = listOf(
            "Time: ${formatTimestamp(log.timestampEpochMs)}",
            "Component: ${log.component}",
            "Request: ${log.requestId?.value ?: "None"}",
            "Fields: ${formatFields(log)}",
        ),
        emphasis = log.level.toEmphasis(),
        openRequestId = log.requestId,
    )

    private fun timelineCard(
        log: StructuredLog,
        originEpochMs: Long,
        sequence: Int,
        total: Int,
    ): ConsoleCard = ConsoleCard(
        title = "${String.format(Locale.US, "%02d", sequence)} · ${log.level.name} · ${log.event}",
        lines = listOf(
            "Sequence: $sequence / $total",
            "Time: ${formatTimestamp(log.timestampEpochMs)}",
            "Offset: ${formatOffset(log.timestampEpochMs - originEpochMs)}",
            "Component: ${log.component}",
            "Fields: ${formatFields(log)}",
        ),
        emphasis = log.level.toEmphasis(),
    )

    private fun healthSummaryCard(results: List<HealthCheckResult>): ConsoleCard {
        if (results.isEmpty()) return emptyCard("No health results recorded", "Health summary")
        val worst = results.minBy { healthRank(it.status) }.status
        return ConsoleCard(
            title = "Health summary",
            lines = listOf(
                "Overall: ${worst.name}",
                "Pass: ${results.count { it.status == HealthStatus.PASS }}",
                "Warn: ${results.count { it.status == HealthStatus.WARN }}",
                "Fail: ${results.count { it.status == HealthStatus.FAIL }}",
                "Not run: ${results.count { it.status == HealthStatus.NOT_RUN }}",
            ),
            emphasis = worst.toEmphasis(),
        )
    }

    private fun healthCard(result: HealthCheckResult): ConsoleCard = ConsoleCard(
        title = "${result.status.name} · ${result.id}",
        lines = listOf(
            "Detail: ${result.detail}",
            "Duration: ${result.durationMs} ms",
        ),
        emphasis = result.status.toEmphasis(),
    )

    private fun resourceCard(snapshot: ResourceSnapshot, title: String? = null): ConsoleCard = ConsoleCard(
        title = title ?: formatTimestamp(snapshot.timestampEpochMs),
        lines = listOf(
            "Process PSS: ${formatBytes(snapshot.processPssBytes)}",
            "Native heap: ${formatBytes(snapshot.nativeHeapBytes)}",
            "Java heap used: ${formatBytes(snapshot.javaHeapUsedBytes)}",
            "Available memory: ${formatBytes(snapshot.availableMemoryBytes)}",
            "Low memory: ${snapshot.lowMemory ?: "Unavailable"}",
            "Thermal: ${snapshot.thermalStatus.name}",
        ),
        emphasis = when {
            snapshot.lowMemory == true -> ConsoleEmphasis.NEGATIVE
            snapshot.thermalStatus.name in THERMAL_WARNING_STATES -> ConsoleEmphasis.WARNING
            else -> ConsoleEmphasis.NEUTRAL
        },
    )

    private fun benchmarkCard(baseline: BenchmarkBaseline): ConsoleCard = ConsoleCard(
        title = "${baseline.key.applicationId.value} · ${baseline.key.useCaseId.value}",
        lines = listOf(
            "Model: ${shortDigest(baseline.key.modelDigest.sha256)}",
            "Load class: ${baseline.key.modelLoadKind.name}",
            "Captured: ${formatTimestamp(baseline.capturedAtEpochMs)}",
            "Samples: ${baseline.sampleCount}",
            "TTFT median / p95: ${formatMetric(baseline.medianTimeToFirstTokenMs, "ms")} / " +
                formatMetric(baseline.p95TimeToFirstTokenMs, "ms"),
            "Total median / p95: ${formatMetric(baseline.medianTotalMs, "ms")} / " +
                formatMetric(baseline.p95TotalMs, "ms"),
            "Decode median: ${formatMetric(baseline.medianDecodeTokensPerSecond, "tok/s")}",
        ),
    )

    private fun emptyCard(message: String, title: String = "Empty state"): ConsoleCard = ConsoleCard(
        title = title,
        lines = listOf(message),
        emphasis = ConsoleEmphasis.WARNING,
    )

    private fun formatFields(log: StructuredLog): String = log.fields.entries
        .sortedBy { it.key }
        .joinToString(separator = " · ") { (key, value) -> "$key=$value" }
        .ifEmpty { "None" }

    private fun formatTimestamp(epochMs: Long): String = timestampFormatter.format(Instant.ofEpochMilli(epochMs))

    private fun formatDuration(value: Long?): String = value?.let { "$it ms" } ?: "Unavailable"

    private fun formatOffset(value: Long): String = if (value >= 0) "+$value ms" else "$value ms"

    private fun formatRate(value: Double?): String = value?.let { String.format(Locale.US, "%.2f tok/s", it) }
        ?: "Unavailable"

    private fun formatMetric(value: Double?, unit: String): String = value
        ?.let { String.format(Locale.US, "%.2f %s", it, unit) }
        ?: "Unavailable"

    private fun formatBytes(value: Long?): String {
        if (value == null) return "Unavailable"
        val mib = value.toDouble() / BYTES_PER_MIB
        return String.format(Locale.US, "%.1f MiB", mib)
    }

    private fun shortId(value: String): String = value.take(SHORT_ID_LENGTH)

    private fun shortDigest(value: String): String = value.take(SHORT_DIGEST_LENGTH)

    private fun healthRank(status: HealthStatus): Int = when (status) {
        HealthStatus.FAIL -> 0
        HealthStatus.WARN -> 1
        HealthStatus.NOT_RUN -> 2
        HealthStatus.PASS -> 3
    }

    private fun RunStatus.toEmphasis(): ConsoleEmphasis = when (this) {
        RunStatus.COMPLETED -> ConsoleEmphasis.POSITIVE
        RunStatus.QUEUED, RunStatus.RUNNING -> ConsoleEmphasis.WARNING
        RunStatus.FAILED, RunStatus.CANCELLED -> ConsoleEmphasis.NEGATIVE
    }

    private fun LogLevel.toEmphasis(): ConsoleEmphasis = when (this) {
        LogLevel.DEBUG, LogLevel.INFO -> ConsoleEmphasis.NEUTRAL
        LogLevel.WARN -> ConsoleEmphasis.WARNING
        LogLevel.ERROR -> ConsoleEmphasis.NEGATIVE
    }

    private fun HealthStatus.toEmphasis(): ConsoleEmphasis = when (this) {
        HealthStatus.PASS -> ConsoleEmphasis.POSITIVE
        HealthStatus.WARN, HealthStatus.NOT_RUN -> ConsoleEmphasis.WARNING
        HealthStatus.FAIL -> ConsoleEmphasis.NEGATIVE
    }

    private companion object {
        const val BYTES_PER_MIB = 1024.0 * 1024.0
        const val SHORT_ID_LENGTH = 12
        const val SHORT_DIGEST_LENGTH = 16
        val THERMAL_WARNING_STATES = setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")
    }
}
