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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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

internal interface HarnessInferenceActivityActions {
    fun refresh()

    fun openDetail(requestId: String)

    fun selectFilter(selection: InferenceActivityFilterSelection)

    fun clearHistory()

    fun clearFeedback()
}

@Composable
internal fun HarnessInferenceActivityScreen(
    state: HarnessInferenceActivityState,
    actions: HarnessInferenceActivityActions,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    InferenceActivityClearDialog(
        visible = confirmClear,
        onConfirm = {
            confirmClear = false
            actions.clearHistory()
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
            onRefresh = actions::refresh,
            modifier = modifier,
        )

        else -> InferenceActivityList(
            state = state,
            actions = actions,
            onRequestClear = { confirmClear = true },
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
    actions: HarnessInferenceActivityActions,
    onRequestClear: () -> Unit,
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
                onRefresh = actions::refresh,
                onRequestClear = onRequestClear,
            )
        }
        item {
            InferenceActivityFilters(
                state = state,
                onSelectApplication = { applicationId ->
                    actions.selectFilter(InferenceActivityFilterSelection.Application(applicationId))
                },
                onSelectStatus = { status ->
                    actions.selectFilter(InferenceActivityFilterSelection.Status(status))
                },
                onSelectPeriod = { period ->
                    actions.selectFilter(InferenceActivityFilterSelection.Period(period))
                },
                onSelectUseCase = { useCaseId ->
                    actions.selectFilter(InferenceActivityFilterSelection.UseCase(useCaseId))
                },
            )
        }
        state.feedback?.let { feedback ->
            item { InferenceActivityFeedback(feedback, actions::clearFeedback) }
        }
        if (state.listErrorCode != null) {
            item { InferenceActivityDegradedCard(state.listErrorCode.name) }
        }
        if (state.items.isEmpty()) {
            item {
                val filtered = !state.filter.isDefault
                HarnessEmptyState(
                    title = if (filtered) "No activity matches these filters" else "No inference activity yet",
                    detail = if (filtered) {
                        "Change or clear a filter to see other local inference activity."
                    } else {
                        "Use an authorized app such as RedactGuard. Accepted inference will appear here " +
                            "and remain available after Harnex restarts."
                    },
                )
            }
        } else {
            items(items = state.items, key = InferenceActivityListItem::requestId) { item ->
                InferenceActivityRow(item = item, onOpenDetail = actions::openDetail)
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
            enabled = state.hasTerminalHistory && !state.mutationInProgress,
            modifier = Modifier.fillMaxWidth().testTag("activity-clear-history"),
            onClick = onRequestClear,
        )
    }
}

@Composable
private fun InferenceActivityFilters(
    state: HarnessInferenceActivityState,
    onSelectApplication: (String?) -> Unit,
    onSelectStatus: (InferenceAuditStatus?) -> Unit,
    onSelectPeriod: (InferenceActivityPeriod) -> Unit,
    onSelectUseCase: (String?) -> Unit,
) {
    val enabled = !state.loading && !state.mutationInProgress
    HarnessCard(modifier = Modifier.fillMaxWidth().testTag("activity-filters")) {
        Text("Filters", style = MaterialTheme.typography.titleMedium)
        ActivityFilterLabel("Application")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small)) {
            item {
                FilterChip(
                    selected = state.filter.applicationId == null,
                    enabled = enabled,
                    onClick = { onSelectApplication(null) },
                    label = { Text("All") },
                )
            }
            items(state.filterOptions.applications, key = InferenceActivityFilterOption::id) { option ->
                FilterChip(
                    selected = state.filter.applicationId == option.id,
                    enabled = enabled,
                    onClick = { onSelectApplication(option.id) },
                    label = { Text(option.label) },
                )
            }
        }

        ActivityFilterLabel("Status")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small)) {
            item {
                FilterChip(
                    selected = state.filter.status == null,
                    enabled = enabled,
                    onClick = { onSelectStatus(null) },
                    label = { Text("All") },
                )
            }
            items(InferenceAuditStatus.entries, key = InferenceAuditStatus::name) { status ->
                FilterChip(
                    selected = state.filter.status == status,
                    enabled = enabled,
                    onClick = { onSelectStatus(status) },
                    label = { Text(status.activityDisplayLabel()) },
                )
            }
        }

        ActivityFilterLabel("Period")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small)) {
            items(InferenceActivityPeriod.entries, key = InferenceActivityPeriod::name) { period ->
                FilterChip(
                    selected = state.filter.period == period,
                    enabled = enabled,
                    onClick = { onSelectPeriod(period) },
                    label = { Text(period.displayLabel) },
                )
            }
        }

        if (state.filterOptions.useCases.isNotEmpty()) {
            ActivityFilterLabel("Use case")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small)) {
                item {
                    FilterChip(
                        selected = state.filter.useCaseId == null,
                        enabled = enabled,
                        onClick = { onSelectUseCase(null) },
                        label = { Text("All") },
                    )
                }
                items(state.filterOptions.useCases, key = InferenceActivityFilterOption::id) { option ->
                    FilterChip(
                        selected = state.filter.useCaseId == option.id,
                        enabled = enabled,
                        onClick = { onSelectUseCase(option.id) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityFilterLabel(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
