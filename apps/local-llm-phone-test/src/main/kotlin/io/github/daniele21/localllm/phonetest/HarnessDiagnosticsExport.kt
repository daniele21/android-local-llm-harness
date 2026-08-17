package io.github.daniele21.localllm.phonetest

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.android.AndroidResourceSnapshotProvider
import io.github.daniele21.localllm.observability.android.ResourceSnapshotRecorder
import java.time.Instant

/**
 * Builds one bounded, privacy-safe diagnostic snapshot that can be copied or shared by a user.
 *
 * The report deliberately excludes prompts, generated output, document/download URIs, filesystem
 * paths and arbitrary backend exception messages. Generation data comes from the existing typed
 * telemetry contracts and logs are projected through [HarnessLogSource]'s allowlisted safe fields.
 */
internal object HarnessDiagnosticsExport {
    private const val REPORT_SCHEMA = "harness-diagnostics-v1"

    fun build(context: Context): String {
        val graph = HarnessRuntimeGraph.from(context)
        val repository = graph.telemetryRepository
        val model = graph.selectedModel
        val runtime = graph.runtimeSnapshot()
        val executionProfile = model?.let { selected ->
            runCatching { resolvedPhonePlaygroundUseCase(selected).model }.getOrNull()
        }
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val resourceCaptureSucceeded = captureFreshResource(context, repository)
        val identitySections = DiagnosticsIdentitySections(
            packageName = context.packageName,
            versionName = packageInfo?.versionName,
            versionCode = packageInfo?.let(PackageInfoCompat::getLongVersionCode),
            memoryInfo = currentMemoryInfo(context),
            model = model,
            executionProfile = executionProfile,
            runtimeState = runtime?.state?.name,
            loadedModel = runtime?.loadedModel?.sha256,
            activeSessions = runtime?.activeSessions,
            queuedRequests = runtime?.queuedRequests,
        )
        val telemetrySections = DiagnosticsTelemetrySections(
            repository = repository,
            selectedModel = model,
            freshResourceCaptureSucceeded = resourceCaptureSucceeded,
        )

        return buildString {
            appendHeader()
            identitySections.appendTo(this)
            telemetrySections.appendTo(this)
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
}
