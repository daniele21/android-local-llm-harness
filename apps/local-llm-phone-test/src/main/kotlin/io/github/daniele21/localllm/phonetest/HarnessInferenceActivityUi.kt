@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessConfirmationDialog
import io.github.daniele21.localllm.ui.designsystem.HarnessEmptyState
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessLoadingState
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing
import java.text.DateFormat
import java.util.Date

@Composable
internal fun HarnessInferenceActivityScreen(
    state: HarnessInferenceActivityState,
    onRefresh: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        HarnessConfirmationDialog(
            title = "Clear inference history?",
            detail = "Completed, failed, cancelled and interrupted local activity records will be deleted. Active inference records, models, app connections and diagnostics are not removed.",
            confirmLabel = "Clear history",
            dismissLabel = "Cancel",
            onConfirm = {
                confirmClear = false
                onClearHistory()
            },
            onDismiss = { confirmClear = false },
        )
    }

    when {
        state.loading && state.items.isEmpty() -> HarnessLoadingState(
            title = "Loading activity",
            detail = "Reading the encrypted local inference ledger",
            modifier = modifier,
        )

        state.listErrorCode != null && state.items.isEmpty() -> Column(modifier = modifier.fillMaxSize()) {
            HarnessErrorState(
                title = "Inference activity unavailable",
                detail = "The local audit store could not be read (${state.listErrorCode.name}). Inference audit remains fail-closed while storage is degraded.",
            )
            HarnessSecondaryButton(
                text = "Retry",
                modifier = Modifier.fillMaxWidth(),
                onClick = onRefresh,
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize().testTag("inference-activity-list"),
            contentPadding = PaddingValues(LocalHarnessSpacing.current.large),
            verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small)) {
                    Text("Activity", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Local inference history: caller, input, output and execution metrics. Sensitive content stays in Harnex and is not part of Diagnostics exports.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HarnessSecondaryButton(
                        text = "Refresh activity",
                        enabled = !state.loading && !state.mutationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRefresh,
                    )
                    HarnessSecondaryButton(
                        text = "Clear completed history",
                        enabled = state.items.any { it.status.isTerminal } && !state.mutationInProgress,
                        modifier = Modifier.fillMaxWidth().testTag("activity-clear-history"),
                        onClick = { confirmClear = true },
                    )
                }
            }
            state.feedback?.let { feedback ->
                item {
                    HarnessCard {
                        Text(feedback, style = MaterialTheme.typography.bodyMedium)
                        HarnessSecondaryButton("Dismiss", onClick = onClearFeedback)
                    }
                }
            }
            if (state.listErrorCode != null) {
                item {
                    HarnessCard {
                        HarnessStatusBadge("AUDIT DEGRADED", HarnessStatusTone.ERROR)
                        Text(
                            "Some activity could not be loaded (${state.listErrorCode.name}).",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.items.isEmpty()) {
                item {
                    HarnessEmptyState(
                        title = "No inference activity yet",
                        detail = "Use an authorized app such as RedactGuard. Accepted inference will appear here and remain available after Harnex restarts.",
                    )
                }
            } else {
                items(items = state.items, key = InferenceActivityListItem::requestId) { item ->
                    InferenceActivityRow(item = item, onOpenDetail = onOpenDetail)
                }
            }
        }
    }
}

@Composable
internal fun HarnessInferenceActivityDetailScreen(
    state: HarnessInferenceActivityState,
    requestId: String,
    onRetry: () -> Unit,
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

        else -> InferenceActivityDetailContent(state.detail, modifier)
    }
}

@Composable
private fun InferenceActivityRow(item: InferenceActivityListItem, onOpenDetail: (String) -> Unit) {
    HarnessCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity-${item.requestId}")
            .clickable { onOpenDetail(item.requestId) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HarnessMinimumTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.applicationLabel, style = MaterialTheme.typography.titleMedium)
                    HarnessStatusBadge(item.status.displayLabel(), item.status.tone())
                }
                Text(
                    item.verifiedPackageName ?: item.applicationId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${item.useCaseId} · ${formatActivityTime(item.receivedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val metrics = listOfNotNull(
                    item.totalMs?.let { "${it} ms" },
                    item.decodeTokensPerSecond?.let { "${formatThroughput(it)} tok/s" },
                    item.modelDigest?.take(10)?.let { "model $it…" },
                ).joinToString(" · ")
                if (metrics.isNotBlank()) {
                    Text(metrics, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun InferenceActivityDetailContent(detail: InferenceActivityDetail, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("inference-activity-detail"),
        contentPadding = PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item {
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
                    HarnessStatusBadge(detail.status.displayLabel(), detail.status.tone())
                }
            }
        }
        item {
            SensitiveContentCard(
                title = "Input",
                value = detail.input,
                emptyLabel = "No input content recorded.",
            )
        }
        detail.effectivePrompt?.let { effectivePrompt ->
            item {
                SensitiveContentCard(
                    title = "Effective prompt",
                    value = effectivePrompt,
                    emptyLabel = "No effective prompt recorded.",
                )
            }
        }
        item {
            SensitiveContentCard(
                title = "Answer",
                value = detail.answerOutput,
                emptyLabel = "No answer output recorded.",
            )
        }
        detail.reasoningOutput?.let { reasoning ->
            item {
                SensitiveContentCard(
                    title = "Reasoning",
                    value = reasoning,
                    emptyLabel = "No reasoning output recorded.",
                )
            }
        }
        item {
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
                ActivityMetric("Decode throughput", detail.decodeTokensPerSecond?.let { "${formatThroughput(it)} tok/s" })
                ActivityMetric("Stop reason", detail.stopReason)
            }
        }
        item {
            HarnessCard {
                Text("Execution identity", style = MaterialTheme.typography.titleMedium)
                ActivityMetric("Request", detail.requestId)
                ActivityMetric("Application ID", detail.applicationId)
                ActivityMetric("Package", detail.verifiedPackageName)
                ActivityMetric("Model", detail.modelDigest)
                ActivityMetric("Model load", detail.modelLoadKind)
                ActivityMetric("Preset", detail.presetId?.let { id -> detail.presetVersion?.let { "$id v$it" } ?: id })
                ActivityMetric("Backend", detail.backendId)
                ActivityMetric("Backend revision", detail.backendRevision)
                ActivityMetric("Backend fingerprint", detail.backendExecutionFingerprint)
                ActivityMetric("Placement", detail.effectivePlacement)
                ActivityMetric("Received", formatActivityTime(detail.receivedAtEpochMs))
                ActivityMetric("Completed", detail.completedAtEpochMs?.let(::formatActivityTime))
                ActivityMetric("Terminal code", detail.terminalCode)
            }
        }
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
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun InferenceAuditStatus.displayLabel(): String = name.replace('_', ' ')

private fun InferenceAuditStatus.tone(): HarnessStatusTone = when (this) {
    InferenceAuditStatus.COMPLETED -> HarnessStatusTone.SUCCESS
    InferenceAuditStatus.FAILED -> HarnessStatusTone.ERROR
    InferenceAuditStatus.CANCELLED,
    InferenceAuditStatus.INTERRUPTED,
    -> HarnessStatusTone.WARNING

    InferenceAuditStatus.ADMITTED,
    InferenceAuditStatus.PREPARED,
    InferenceAuditStatus.RUNNING,
    -> HarnessStatusTone.INFO
}

private fun formatActivityTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(epochMs))

private fun formatThroughput(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
