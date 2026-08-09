package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkExecutionIdentity
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.benchmark.BenchmarkBaselineRecorder
import io.github.daniele21.localllm.observability.benchmark.BenchmarkCaptureResult
import io.github.daniele21.localllm.observability.benchmark.BenchmarkRegressionHealthCheck
import java.util.Locale

internal data class BenchmarkUi(
    val stableId: String,
    val useCase: String,
    val loadKind: String,
    val samples: String,
    val medianTtft: String,
    val p95Total: String,
    val medianDecode: String,
    val regressionStatus: String,
    val regressionDetail: String,
)

internal data class BenchmarkHistoryUi(
    val stableId: String,
    val useCase: String,
    val loadKind: String,
    val capturedAt: String,
    val samples: String,
    val medianTtft: String,
    val p95Total: String,
    val medianDecode: String,
    val active: Boolean,
)

internal data class BenchmarkReadinessUi(
    val stableId: String,
    val useCase: String,
    val loadKind: String,
    val baselineSamples: Int,
    val baselineRequired: Int,
    val comparisonSamples: Int,
    val comparisonRequired: Int,
    val baselineCaptured: Boolean,
    val captureReady: Boolean,
    val comparisonReady: Boolean,
    val detail: String,
)

internal data class BenchmarkUiState(
    val baselines: List<BenchmarkUi> = emptyList(),
    val readiness: List<BenchmarkReadinessUi> = emptyList(),
    val history: List<BenchmarkHistoryUi> = emptyList(),
    val eligibleKeys: Int = 0,
    val captureDetail: String? = null,
    val sourceError: String? = null,
)

internal class HarnessBenchmarkSource(private val repository: TelemetryRepository, private val selectedModel: () -> ImportedPhoneModel?) {
    private val recorder = BenchmarkBaselineRecorder(repository)

    fun snapshot(captureDetail: String? = null): BenchmarkUiState = runCatching {
        val model = selectedModel()
        val keys = model?.let(::knownKeys).orEmpty()
        val activeBaselines = repository.benchmarkBaselines()
        BenchmarkUiState(
            baselines = activeBaselines
                .filter { model == null || it.key.modelDigest == model.digest }
                .sortedWith(compareBy({ it.key.useCaseId.value }, { it.key.modelLoadKind.name }))
                .map(::toUi),
            readiness = keys.map(::toReadinessUi),
            history = repository.benchmarkBaselineHistory(HISTORY_LIMIT)
                .filter { model == null || it.key.modelDigest == model.digest }
                .map { baseline -> baseline.toHistoryUi(activeBaselines) },
            eligibleKeys = keys.size,
            captureDetail = captureDetail,
        )
    }.getOrElse {
        BenchmarkUiState(sourceError = SOURCE_ERROR, captureDetail = captureDetail)
    }

    fun capture(stableId: String): BenchmarkUiState {
        val model = selectedModel()
        val key = model?.let { selected ->
            knownKeys(selected).firstOrNull { it.safeStableId() == stableId }
        }
        return when {
            model == null -> snapshot("Select a model before capturing a benchmark baseline.")
            key == null -> snapshot("The selected benchmark key is no longer available.")
            else -> capture(key)
        }
    }

    fun captureEligible(): BenchmarkUiState {
        val model = selectedModel()
            ?: return snapshot("Select a model before capturing a benchmark baseline.")
        val knownKeys = knownKeys(model)
        if (knownKeys.isEmpty()) {
            return snapshot("No completed cold or warm runs are available for this model.")
        }
        val readiness = knownKeys.map(::toReadinessUi)
        val readyKeys = knownKeys.zip(readiness)
            .filter { (_, state) -> state.captureReady }
            .map { (key, _) -> key }
        if (readyKeys.isEmpty()) {
            val uncaptured = readiness.any { !it.baselineCaptured }
            return snapshot(
                if (uncaptured) {
                    "Benchmark baselines need at least $BASELINE_REQUIRED_SAMPLES matching completed runs per cold/warm key."
                } else {
                    "No benchmark keys are currently ready for baseline capture."
                },
            )
        }
        val results = readyKeys.map(recorder::capture)
        val captured = results.count { it is BenchmarkCaptureResult.Captured }
        val insufficient = results.filterIsInstance<BenchmarkCaptureResult.InsufficientSamples>()
        val detail = when {
            captured > 0 && insufficient.isEmpty() -> "Captured $captured benchmark baseline(s)."
            captured > 0 -> "Captured $captured baseline(s); ${insufficient.size} key(s) changed before capture."
            else -> "Benchmark samples changed before capture; refresh readiness and try again."
        }
        return snapshot(detail)
    }

    private fun capture(key: BenchmarkKey): BenchmarkUiState {
        val readiness = toReadinessUi(key)
        return when {
            readiness.baselineCaptured -> snapshot("The selected benchmark baseline has already been captured.")

            !readiness.captureReady -> snapshot(readiness.detail)

            else -> when (val result = recorder.capture(key)) {
                is BenchmarkCaptureResult.Captured -> snapshot(
                    "Captured ${key.useCaseId.value} ${key.modelLoadKind.name.lowercase()} baseline.",
                )

                is BenchmarkCaptureResult.InsufficientSamples -> snapshot(
                    "${key.useCaseId.value} ${key.modelLoadKind.name.lowercase()} needs " +
                        "${result.required - result.available} more completed sample(s).",
                )
            }
        }
    }

    private fun knownKeys(model: ImportedPhoneModel): List<BenchmarkKey> {
        val runKeys = repository.recentRuns(RUN_LOOKBACK)
            .asSequence()
            .filter { it.status == RunStatus.COMPLETED }
            .filter { it.modelDigest == model.digest }
            .filter { it.modelLoadKind != ModelLoadKind.UNKNOWN }
            .map { it.benchmarkKey() }
        val baselineKeys = repository.benchmarkBaselines()
            .asSequence()
            .map(BenchmarkBaseline::key)
            .filter { it.modelDigest == model.digest }
        return (runKeys + baselineKeys)
            .distinctBy { it.safeStableId() }
            .sortedWith(compareBy({ it.useCaseId.value }, { it.modelLoadKind.name }))
            .toList()
    }

    private fun toReadinessUi(key: BenchmarkKey): BenchmarkReadinessUi {
        val matching = repository.recentRuns(RUN_LOOKBACK)
            .filter { it.matches(key) }
            .sortedByDescending { it.completedAtEpochMs }
        val baseline = repository.benchmarkBaselines().firstOrNull { it.key == key }
        val baselineSamples = baseline?.sampleCount ?: matching.take(BASELINE_WINDOW_SIZE).size
        val comparisonSamples = baseline?.let { captured ->
            matching.count { requireNotNull(it.completedAtEpochMs) > captured.capturedAtEpochMs }
                .coerceAtMost(COMPARISON_WINDOW_SIZE)
        } ?: 0
        val captureReady = baseline == null && baselineSamples >= BASELINE_REQUIRED_SAMPLES
        val comparisonReady = baseline != null && comparisonSamples >= COMPARISON_REQUIRED_SAMPLES
        val detail = when {
            captureReady -> "Ready to capture a baseline."
            baseline == null -> "Needs ${BASELINE_REQUIRED_SAMPLES - baselineSamples} more baseline sample(s)."
            comparisonReady -> "Ready for regression evaluation."
            else -> "Needs ${COMPARISON_REQUIRED_SAMPLES - comparisonSamples} more post-baseline sample(s)."
        }
        return BenchmarkReadinessUi(
            stableId = key.safeStableId(),
            useCase = key.useCaseId.value,
            loadKind = key.modelLoadKind.name,
            baselineSamples = baselineSamples,
            baselineRequired = BASELINE_REQUIRED_SAMPLES,
            comparisonSamples = comparisonSamples,
            comparisonRequired = COMPARISON_REQUIRED_SAMPLES,
            baselineCaptured = baseline != null,
            captureReady = captureReady,
            comparisonReady = comparisonReady,
            detail = detail,
        )
    }

    private fun GenerationRunRecord.benchmarkKey(): BenchmarkKey = BenchmarkKey(
        applicationId = applicationId,
        useCaseId = useCaseId,
        modelDigest = modelDigest,
        modelLoadKind = modelLoadKind,
        executionIdentity = BenchmarkExecutionIdentity.fromRun(this),
    )

    private fun GenerationRunRecord.matches(key: BenchmarkKey): Boolean = status == RunStatus.COMPLETED &&
        completedAtEpochMs != null &&
        key.matches(this)

    private fun toUi(baseline: BenchmarkBaseline): BenchmarkUi {
        val assessment = BenchmarkRegressionHealthCheck(repository, baseline.key).evaluate()
        return BenchmarkUi(
            stableId = baseline.key.safeStableId(),
            useCase = baseline.key.useCaseId.value,
            loadKind = baseline.key.modelLoadKind.name,
            samples = baseline.sampleCount.toString(),
            medianTtft = baseline.medianTimeToFirstTokenMs.asMilliseconds(),
            p95Total = baseline.p95TotalMs.asMilliseconds(),
            medianDecode = baseline.medianDecodeTokensPerSecond.asThroughput(),
            regressionStatus = assessment.status.name,
            regressionDetail = assessment.detail,
        )
    }

    private companion object {
        const val RUN_LOOKBACK = 500
        const val HISTORY_LIMIT = 100
        const val BASELINE_WINDOW_SIZE = 20
        const val COMPARISON_WINDOW_SIZE = 10
        const val BASELINE_REQUIRED_SAMPLES = 5
        const val COMPARISON_REQUIRED_SAMPLES = 3
        const val SOURCE_ERROR = "Benchmark diagnostics are temporarily unavailable."
    }
}

private fun BenchmarkBaseline.toHistoryUi(activeBaselines: List<BenchmarkBaseline>): BenchmarkHistoryUi = BenchmarkHistoryUi(
    stableId = "${key.safeStableId()}:$capturedAtEpochMs",
    useCase = key.useCaseId.value,
    loadKind = key.modelLoadKind.name,
    capturedAt = java.time.Instant.ofEpochMilli(capturedAtEpochMs).toString(),
    samples = sampleCount.toString(),
    medianTtft = medianTimeToFirstTokenMs.asMilliseconds(),
    p95Total = p95TotalMs.asMilliseconds(),
    medianDecode = medianDecodeTokensPerSecond.asThroughput(),
    active = activeBaselines.any { it == this },
)

private fun BenchmarkKey.safeStableId(): String = stableId

private fun Double?.asMilliseconds(): String = this?.let { "%.1f ms".format(Locale.ROOT, it) } ?: "Unavailable"

private fun Double?.asThroughput(): String = this?.let { "%.2f tok/s".format(Locale.ROOT, it) } ?: "Unavailable"
