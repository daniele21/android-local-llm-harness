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
    InferenceActivityClearDialog(
        visible = confirmClear,
        onConfirm = {
            confirmClear = false
            onClearHistory()
        },
        onDismiss = { confirmClear = false },
    )

    when {
        state.loading && state.items.isEmpty() -> HarnessLoadingState(
            title = "Loading activity",
            detail = "Reading the encrypted local inference ledger",
            modifier = modifier,
        )

        state.listErrorCode != null && state.items.isEmpty() -> InferenceActivityUnavailable(
            state = state,
            onRefresh = onRefresh,
            modifier = modifier,
        )

        else -> InferenceActivityList(
            state = state,
            onRefresh = onRefresh,
            onOpenDetail = onOpenDetail,
            onRequestClear = { confirmClear = true },
            onClearFeedback = onClearFeedback,
            modifier = modifier,
        )
    }
}

@Composable
private fun InferenceActivityClearDialog(visible: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!visible) return
    HarnessConfirmationDialog(
        title = "Clear inference history?",
        detail =
        "Completed, failed, cancelled and interrupted local activity records will be deleted. " +
            "Active inference records, models, app connections and diagnostics are not removed.",
        confirmLabel = "Clear history",
        dismissLabel = "Cancel",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun InferenceActivityUnavailable(state: HarnessInferenceActivityState, onRefresh: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        HarnessErrorState(
            title = "Inference activity unavailable",
            detail =
            "The local audit store could not be read (${state.listErrorCode?.name}). " +
                "Inference audit remains fail-closed while storage is degraded.",
        )
        HarnessSecondaryButton(
            text = "Retry",
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefresh,
        )
    }
}

@Composable
private fun InferenceActivityList(
    state: HarnessInferenceActivityState,
    onRefresh: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onRequestClear: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("inference-activity-list"),
        contentPadding = PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item {
            InferenceActivityListHeader(
                state = state,
                onRefresh = onRefresh,
                onRequestClear = onRequestClear,
            )
        }
        state.feedback?.let { feedback ->
            item { InferenceActivityFeedback(feedback, onClearFeedback) }
        }
        if (state.listErrorCode != null) {
            item { InferenceActivityDegradedCard(state.listErrorCode.name) }
        }
        if (state.items.isEmpty()) {
            item {
                HarnessEmptyState(
                    title = "No inference activity yet",
                    detail =
                    "Use an authorized app such as RedactGuard. Accepted inference will appear here " +
                        "and remain available after Harnex restarts.",
                )
            }
        } else {
            items(items = state.items, key = InferenceActivityListItem::requestId) { item ->
                InferenceActivityRow(item = item, onOpenDetail = onOpenDetail)
            }
        }
    }
}

@Composable
private fun InferenceActivityListHeader(state: HarnessInferenceActivityState, onRefresh: () -> Unit, onRequestClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small)) {
        Text("Activity", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Local inference history: caller, input, output and execution metrics. " +
                "Sensitive content stays in Harnex and is not part of Diagnostics exports.",
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
            onClick = onRequestClear,
        )
    }
}

@Composable
private fun InferenceActivityFeedback(feedback: String, onClearFeedback: () -> Unit) {
    HarnessCard {
        Text(feedback, style = MaterialTheme.typography.bodyMedium)
        HarnessSecondaryButton("Dismiss", onClick = onClearFeedback)
    }
}

@Composable
private fun InferenceActivityDegradedCard(errorCode: String) {
    HarnessCard {
        HarnessStatusBadge("AUDIT DEGRADED", HarnessStatusTone.ERROR)
        Text(
            "Some activity could not be loaded ($errorCode).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                    HarnessStatusBadge(item.status.activityDisplayLabel(), item.status.activityTone())
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
                    item.totalMs?.let { "$it ms" },
                    item.decodeTokensPerSecond?.let { "${formatActivityThroughput(it)} tok/s" },
                    item.modelDigest?.take(10)?.let { "model $it…" },
                ).joinToString(" · ")
                if (metrics.isNotBlank()) {
                    Text(metrics, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
