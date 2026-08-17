package io.github.daniele21.localllm.phonetest

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.android.AndroidResourceSnapshotProvider
import io.github.daniele21.localllm.observability.android.ResourceSnapshotRecorder
import java.time.Instant
import java.util.Locale

/**
 * Builds one bounded, privacy-safe diagnostic snapshot that can be copied or shared by a user.
 *
 * The report deliberately excludes prompts, generated output, document/download URIs, filesystem
 * paths and arbitrary backend exception messages. Generation data comes from the existing typed
 * telemetry contracts and logs are projected through [HarnessLogSource]'s allowlisted safe fields.
 */
internal object HarnessDiagnosticsExport {
    private const val RUN_LIMIT = 200
    private const val LOG_LIMIT = 250
    private const val RESOURCE_LIMIT = 200
    private const val BENCHMARK_HISTORY_LIMIT = 100
    private const val REPORT_SCHEMA = "harness-diagnostics-v1"

    fun build(context: Context): String {
        val graph = HarnessRuntimeGraph.from(context)
        val repository = graph.telemetryRepository
        val model = graph.selectedModel
        val runtime = graph.runtimeSnapshot()
        val executionProfile = model?.let { selected ->
            runCatching { resolvedPhonePlaygroundUseCase(selected).model }.getOrNull()
        }
        val resourceCaptureSucceeded = captureFreshResource(context, repository)
        val benchmarkState = HarnessBenchmarkSource(repository) { model }.snapshot()
        val memoryInfo = currentMemoryInfo(context)
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()

        return buildString {
            appendHeader()
            appendAppSection(
                packageName = context.packageName,
                versionName = packageInfo?.versionName,
                versionCode = packageInfo?.let(PackageInfoCompat::getLongVersionCode),
            )
            appendDeviceSection(memoryInfo)
            appendModelSection(model)
            appendExecutionProfileSection(executionProfile)
            appendRuntimeSection(
                state = runtime?.state?.name,
                loadedModel = runtime?.loadedModel?.sha256,
                activeSessions = runtime?.activeSessions,
                queuedRequests = runtime?.queuedRequests,
            )
            appendRunsSection(repository)
            appendResourcesSection(repository, resourceCaptureSucceeded)
            appendHealthSection(repository)
            appendBenchmarkReadinessSection(benchmarkState)
            appendBenchmarkBaselinesSection(repository)
            appendLogsSection(repository)
        }
    }

    fun copy(context: Context) {
        val report = build(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Harness diagnostics", report))
        Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
    }

    fun share(context: Context) {
        val report = build(context)
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Harness diagnostics")
                putExtra(Intent.EXTRA_TEXT, report)
            },
            "Export diagnostics",
        )
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Deterministic, content-free projection of one run for export and unit coverage. */
    internal fun renderRun(index: Int, run: GenerationRunRecord): String = buildString {
        append("run[$index]")
        append(" request=${run.requestId.value.take(12)}…")
        append(" app=${run.applicationId.value.safeToken()}")
        append(" useCase=${run.useCaseId.value.safeToken()}")
        append(" model=${run.modelDigest.sha256.take(12)}…")
        append(" status=${run.status.name}")
        append(" loadKind=${run.modelLoadKind.name}")
        append(" started=${Instant.ofEpochMilli(run.startedAtEpochMs)}")
        append(" completed=${run.completedAtEpochMs.asInstantOrUnavailable()}")
        append(" queueMs=${run.queueMs.orUnavailable()}")
        append(" modelLoadMs=${run.modelLoadMs.orUnavailable()}")
        append(" promptPlanningMs=${run.promptPlanningMs.orUnavailable()}")
        append(" contextCreationMs=${run.contextCreationMs.orUnavailable()}")
        append(" ttftMs=${run.timeToFirstTokenMs.orUnavailable()}")
        append(" prefillMs=${run.prefillMs.orUnavailable()}")
        append(" decodeMs=${run.decodeMs.orUnavailable()}")
        append(" totalMs=${run.totalMs.orUnavailable()}")
        append(" inputTokens=${run.inputTokens.orUnavailable()}")
        append(" outputTokens=${run.outputTokens.orUnavailable()}")
        append(" decodeTokPerSec=${run.decodeTokensPerSecond.asDecimalOrUnavailable()}")
        append(" contextSize=${run.contextSize.orUnavailable()}")
        append(" promptTokens=${run.promptTokenCount.orUnavailable()}")
        append(" maxOutputTokens=${run.maxOutputTokens.orUnavailable()}")
        append(" preset=${run.presetId?.value.asSafeOrNone()}")
        append(" presetVersion=${run.presetVersion.orUnavailable()}")
        append(" thinking=${run.thinkingMode?.name.asSafeOrUnavailable()}")
        append(" temperature=${run.temperature.orUnavailable()}")
        append(" topP=${run.topP.orUnavailable()}")
        append(" topK=${run.topK.orUnavailable()}")
        append(" minP=${run.minP.orUnavailable()}")
        append(" presencePenalty=${run.presencePenalty.orUnavailable()}")
        append(" repeatPenalty=${run.repeatPenalty.orUnavailable()}")
        append(" repeatLastN=${run.repeatLastN.orUnavailable()}")
        append(" seedPolicy=${run.seedPolicy?.name.asSafeOrUnavailable()}")
        append(" stopReason=${run.stopReason?.name.asSafeOrUnavailable()}")
        append(" errorCode=${run.errorCode.asSafeOrNone()}")
    }

    private fun captureFreshResource(context: Context, repository: TelemetryRepository): Boolean = runCatching {
        ResourceSnapshotRecorder(
            AndroidResourceSnapshotProvider(context),
            repository,
        ).capture()
    }.isSuccess

    private fun currentMemoryInfo(context: Context): ActivityManager.MemoryInfo = ActivityManager.MemoryInfo().also { info ->
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
    }

    private fun StringBuilder.appendHeader() {
        appendLine("# Android Local LLM Harness diagnostics")
        appendLine("schema=$REPORT_SCHEMA")
        appendLine("generatedAt=${Instant.now()}")
        appendLine("privacy=prompt/output/uri/path/backend-message excluded")
        appendLine()
    }

    private fun StringBuilder.appendAppSection(packageName: String, versionName: String?, versionCode: Long?) {
        appendLine("[app]")
        appendLine("package=$packageName")
        appendLine("versionName=${versionName.asSafeOrUnavailable()}")
        appendLine("versionCode=${versionCode.orUnavailable()}")
        appendLine("buildType=${BuildConfig.BUILD_TYPE}")
        appendLine()
    }

    private fun StringBuilder.appendDeviceSection(memoryInfo: ActivityManager.MemoryInfo) {
        appendLine("[device]")
        appendLine("manufacturer=${Build.MANUFACTURER.safeToken()}")
        appendLine("model=${Build.MODEL.safeToken()}")
        appendLine("androidRelease=${Build.VERSION.RELEASE.safeToken()}")
        appendLine("androidApi=${Build.VERSION.SDK_INT}")
        appendLine("abis=${Build.SUPPORTED_ABIS.joinToString().safeToken()}")
        appendLine("availableProcessors=${Runtime.getRuntime().availableProcessors()}")
        appendLine("totalRamBytes=${memoryInfo.totalMem}")
        appendLine("availableRamBytes=${memoryInfo.availMem}")
        appendLine("lowMemory=${memoryInfo.lowMemory}")
        appendLine("memoryThresholdBytes=${memoryInfo.threshold}")
        appendLine()
    }

    private fun StringBuilder.appendModelSection(model: ImportedPhoneModel?) {
        appendLine("[model]")
        if (model == null) {
            appendLine("selected=false")
        } else {
            appendLine("selected=true")
            appendLine("digest=${model.digest.sha256}")
            appendLine("fileName=${model.fileName.safeToken()}")
            appendLine("sizeBytes=${model.sizeBytes}")
            appendLine("architecture=${model.architecture.safeToken()}")
            appendLine("quantization=${model.quantization.safeToken()}")
        }
        appendLine()
    }

    private fun StringBuilder.appendExecutionProfileSection(profile: GgufModelProfile?) {
        appendLine("[execution-profile]")
        if (profile == null) {
            appendLine("available=false")
        } else {
            appendLine("available=true")
            appendLine("profileId=${profile.id.safeToken()}")
            appendLine("contextSize=${profile.contextSize}")
            appendLine("batchSize=${profile.batchSize}")
            appendLine("microBatchSize=${profile.microBatchSize}")
            appendLine("cpuThreads=${profile.cpuThreads}")
            appendLine("batchThreads=${profile.batchThreads}")
            appendLine("gpuLayers=${profile.gpuLayers}")
            appendLine("useMmap=${profile.useMmap}")
            appendLine("useMlock=${profile.useMlock}")
            appendLine("flashAttention=${profile.flashAttention}")
        }
        appendLine()
    }

    private fun StringBuilder.appendRuntimeSection(state: String?, loadedModel: String?, activeSessions: Int?, queuedRequests: Int?) {
        appendLine("[runtime]")
        appendLine("available=${state != null}")
        appendLine("state=${state.asSafeOrUnavailable()}")
        appendLine("loadedModel=${loadedModel.asSafeOrNone()}")
        appendLine("activeSessions=${activeSessions.orUnavailable()}")
        appendLine("queuedRequests=${queuedRequests.orUnavailable()}")
        appendLine()
    }

    private fun StringBuilder.appendRunsSection(repository: TelemetryRepository) {
        val runs = repository.recentRuns(RUN_LIMIT)
        appendLine("[generation-runs]")
        appendLine("count=${runs.size}")
        runs.forEachIndexed { index, run -> appendLine(renderRun(index, run)) }
        appendLine()
    }

    private fun StringBuilder.appendResourcesSection(repository: TelemetryRepository, freshCaptureSucceeded: Boolean) {
        val resources = repository.recentResourceSnapshots(RESOURCE_LIMIT)
        appendLine("[resources]")
        appendLine("freshCapture=${if (freshCaptureSucceeded) "captured" else "unavailable"}")
        appendLine("count=${resources.size}")
        resources.forEachIndexed { index, resource -> appendResource(index, resource) }
        appendLine()
    }

    private fun StringBuilder.appendHealthSection(repository: TelemetryRepository) {
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

    private fun StringBuilder.appendBenchmarkReadinessSection(state: BenchmarkUiState) {
        appendLine("[benchmark-readiness]")
        appendLine("eligibleKeys=${state.eligibleKeys}")
        appendLine("sourceError=${state.sourceError.asSafeOrNone()}")
        state.readiness.forEachIndexed { index, readiness ->
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

    private fun StringBuilder.appendBenchmarkBaselinesSection(repository: TelemetryRepository) {
        val baselines = repository.benchmarkBaselines()
        val history = repository.benchmarkBaselineHistory(BENCHMARK_HISTORY_LIMIT)
        appendLine("[benchmark-baselines]")
        appendLine("activeCount=${baselines.size}")
        baselines.forEachIndexed { index, baseline -> appendBaseline("baseline", index, baseline) }
        appendLine("historyCount=${history.size}")
        history.forEachIndexed { index, baseline -> appendBaseline("history", index, baseline) }
        appendLine()
    }

    private fun StringBuilder.appendLogsSection(repository: TelemetryRepository) {
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

    private fun Any?.orUnavailable(): String = this?.toString() ?: "Unavailable"

    private fun Long?.asInstantOrUnavailable(): String = this?.let(Instant::ofEpochMilli)?.toString() ?: "Unavailable"

    private fun Double?.asDecimalOrUnavailable(): String = this?.let { "%.3f".format(Locale.ROOT, it) } ?: "Unavailable"

    private fun String?.asSafeOrUnavailable(): String = this?.safeToken() ?: "Unavailable"

    private fun String?.asSafeOrNone(): String = this?.safeToken() ?: "None"

    private fun String.safeToken(): String = replace(CONTROL_CHARACTERS, " ").trim().take(MAX_TOKEN_LENGTH)

    private fun String.safeValue(): String = replace(CONTROL_CHARACTERS, " ").trim().take(MAX_VALUE_LENGTH)

    private val CONTROL_CHARACTERS = Regex("[\\p{Cntrl}&&[^\\n\\t]]|[\\n\\t]+")
    private const val MAX_TOKEN_LENGTH = 128
    private const val MAX_VALUE_LENGTH = 512
}
