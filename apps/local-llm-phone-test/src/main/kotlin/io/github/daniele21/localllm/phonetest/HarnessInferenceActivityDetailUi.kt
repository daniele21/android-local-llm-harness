@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessLoadingState
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

@Composable
internal fun HarnessInferenceActivityDetailScreen(
    state: HarnessInferenceActivityState,
    requestId: String,
    onRetry: () -> Unit,
    onOpenTechnicalTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.selectedRequestId != requestId || state.detailLoading -> HarnessLoadingState(
            title = "Loading inference",
            detail = "Decrypting the selected local activity record",
            modifier = modifier,
        )

        state.detail == null -> Column(modifier = modifier.fillMaxSize()) {
            HarnessErrorState(
                title = "Inference record unavailable",
                detail = state.detailErrorCode?.let { "The selected record could not be read (${it.name})." }
                    ?: "The selected record no longer exists.",
            )
            HarnessSecondaryButton("Retry", onClick = onRetry)
        }

        else -> InferenceActivityDetailContent(state.detail, onOpenTechnicalTimeline, modifier)
    }
}

@Composable
private fun InferenceActivityDetailContent(detail: InferenceActivityDetail, onOpenTechnicalTimeline: () -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("inference-activity-detail"),
        contentPadding = PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item { InferenceActivityDetailHeader(detail) }
        item {
            SensitiveContentCard(
                title = "Input",
                value = detail.input,
                emptyLabel = "No input content recorded.",
            )
        }
        detail.effectivePrompt?.let { effectivePrompt ->
            item { SensitiveContentCard("Effective prompt", effectivePrompt, "No effective prompt recorded.") }
        }
        item {
            SensitiveContentCard(
                title = "Answer",
                value = detail.answerOutput,
                emptyLabel = "No answer output recorded.",
            )
        }
        detail.reasoningOutput?.let { reasoning ->
            item { SensitiveContentCard("Reasoning", reasoning, "No reasoning output recorded.") }
        }
        item { InferenceActivityMetricsCard(detail) }
        item { InferenceActivityExecutionCard(detail) }
        item {
            HarnessSecondaryButton(
                text = "Open technical timeline",
                modifier = Modifier.fillMaxWidth().testTag("activity-open-technical-timeline"),
                onClick = onOpenTechnicalTimeline,
            )
        }
    }
}

@Composable
private fun InferenceActivityDetailHeader(detail: InferenceActivityDetail) {
    HarnessCard(emphasized = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.applicationLabel, style = MaterialTheme.typography.titleLarge)
                Text(
                    detail.verifiedPackageName ?: detail.applicationId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(detail.useCaseId, style = MaterialTheme.typography.bodySmall)
            }
            HarnessStatusBadge(detail.status.activityDisplayLabel(), detail.status.activityTone())
        }
    }
}

@Composable
private fun InferenceActivityMetricsCard(detail: InferenceActivityDetail) {
    HarnessCard {
        Text("Inference metrics", style = MaterialTheme.typography.titleMedium)
        ActivityMetric("Total", detail.totalMs?.let { "$it ms" })
        ActivityMetric("Queue", detail.queueMs?.let { "$it ms" })
        ActivityMetric("Model load", detail.modelLoadMs?.let { "$it ms" })
        ActivityMetric("Time to first token", detail.timeToFirstTokenMs?.let { "$it ms" })
        ActivityMetric("Time to first answer", detail.timeToFirstAnswerMs?.let { "$it ms" })
        ActivityMetric("Prefill", detail.prefillMs?.let { "$it ms" })
        ActivityMetric("Decode", detail.decodeMs?.let { "$it ms" })
        ActivityMetric("Prompt planning", detail.promptPlanningMs?.let { "$it ms" })
        ActivityMetric("Context creation", detail.contextCreationMs?.let { "$it ms" })
        ActivityMetric("Input tokens", detail.inputTokens?.toString())
        ActivityMetric("Output tokens", detail.outputTokens?.toString())
        ActivityMetric("Reasoning tokens", detail.reasoningTokens?.toString())
        ActivityMetric("Answer tokens", detail.answerTokens?.toString())
        ActivityMetric(
            "Decode throughput",
            detail.decodeTokensPerSecond?.let { "${formatActivityThroughput(it)} tok/s" },
        )
        ActivityMetric("Stop reason", detail.stopReason)
    }
}

@Composable
private fun InferenceActivityExecutionCard(detail: InferenceActivityDetail) {
    HarnessCard {
        Text("Execution identity", style = MaterialTheme.typography.titleMedium)
        ActivityMetric("Request", detail.requestId)
        ActivityMetric("Application ID", detail.applicationId)
        ActivityMetric("Package", detail.verifiedPackageName)
        ActivityMetric("Model", detail.modelDigest)
        ActivityMetric("Model load", detail.modelLoadKind)
        ActivityMetric(
            "Preset",
            detail.presetId?.let { id -> detail.presetVersion?.let { "$id v$it" } ?: id },
        )
        ActivityMetric("Backend", detail.backendId)
        ActivityMetric("Backend revision", detail.backendRevision)
        ActivityMetric("Backend fingerprint", detail.backendExecutionFingerprint)
        ActivityMetric("Placement", detail.effectivePlacement)
        ActivityMetric("Received", formatActivityTime(detail.receivedAtEpochMs))
        ActivityMetric("Completed", detail.completedAtEpochMs?.let(::formatActivityTime))
        ActivityMetric("Terminal code", detail.terminalCode)
    }
}

@Composable
private fun SensitiveContentCard(title: String, value: String?, emptyLabel: String) {
    HarnessCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HarnessStatusBadge("LOCAL SENSITIVE", HarnessStatusTone.WARNING)
        }
        SelectionContainer {
            Text(
                value?.takeIf(String::isNotBlank) ?: emptyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun ActivityMetric(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
    }
}
