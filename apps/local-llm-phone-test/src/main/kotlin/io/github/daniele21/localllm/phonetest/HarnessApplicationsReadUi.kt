@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessEmptyState
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessLoadingState
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

@Composable
internal fun HarnessApplicationsScreen(
    state: HarnessApplicationsReadState,
    onRefresh: () -> Unit,
    onOpenApplication: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HarnessApplicationsReadState.Loading -> HarnessLoadingState(
            title = "Loading applications",
            detail = "Reading shared-runtime application configuration",
            modifier = modifier,
        )

        is HarnessApplicationsReadState.Failed -> Column(modifier = modifier.fillMaxSize()) {
            HarnessErrorState(
                title = "Applications unavailable",
                detail = state.message,
            )
            HarnessSecondaryButton(
                text = "Retry",
                modifier = Modifier.padding(horizontal = LocalHarnessSpacing.current.large),
                onClick = onRefresh,
            )
        }

        is HarnessApplicationsReadState.Loaded -> {
            if (state.snapshot.applications.isEmpty()) {
                HarnessEmptyState(
                    title = "No applications connected",
                    detail = "Applications authorized to use the Harness shared runtime will appear here.",
                    modifier = modifier,
                )
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize().testTag("applications-list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(LocalHarnessSpacing.current.large),
                    verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall)) {
                            Text("Applications", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Apps using the Harness shared runtime",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(
                        items = state.snapshot.applications,
                        key = HarnessApplicationSummary::applicationId,
                    ) { application ->
                        HarnessApplicationRow(application, onOpenApplication)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HarnessApplicationDetailScreen(
    application: HarnessApplicationSummary?,
    onOpenAssignment: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (application == null) {
        HarnessErrorState(
            title = "Application unavailable",
            detail = "The application configuration may have changed. Return to Applications and reload.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("application-detail-${application.applicationId}"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item {
            HarnessCard(emphasized = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall),
                    ) {
                        Text(application.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            application.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HarnessStatusBadge(
                        label = application.status.label(),
                        tone = application.status.tone(),
                    )
                }
            }
        }
        item {
            Text("Assigned use cases", style = MaterialTheme.typography.titleMedium)
        }
        if (application.assignments.isEmpty()) {
            item {
                HarnessEmptyState(
                    title = "No use cases assigned",
                    detail = "This application currently has no Harness use-case assignment.",
                )
            }
        } else {
            items(
                items = application.assignments,
                key = { it.bindingId + ":" + it.bindingRevision },
            ) { assignment ->
                HarnessAssignmentRow(
                    assignment = assignment,
                    onClick = { onOpenAssignment(application.applicationId, assignment.useCaseId) },
                )
            }
        }
    }
}

@Composable
private fun HarnessApplicationRow(application: HarnessApplicationSummary, onOpenApplication: (String) -> Unit) {
    HarnessCard(
        modifier = Modifier
            .testTag("application-${application.applicationId}")
            .clickable { onOpenApplication(application.applicationId) },
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
                Text(application.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    application.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    assignmentCountLabel(application.assignments.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(application.status.label(), application.status.tone())
        }
    }
}

@Composable
private fun HarnessAssignmentRow(assignment: HarnessAssignmentSummary, onClick: () -> Unit) {
    HarnessCard(
        modifier = Modifier
            .testTag("assignment-${assignment.useCaseId}")
            .clickable(onClick = onClick),
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
                Text(assignment.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    assignment.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    assignment.defaultPreset?.let { "Default preset: ${it.displayName}" } ?: "Default preset unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(assignment.status.label(), assignment.status.tone())
        }
    }
}

internal fun HarnessApplicationStatus.label(): String = when (this) {
    HarnessApplicationStatus.AUTHORIZED -> "Authorized"
    HarnessApplicationStatus.PENDING -> "Pending"
    HarnessApplicationStatus.DISABLED -> "Disabled"
    HarnessApplicationStatus.IDENTITY_CHANGED -> "Identity changed"
    HarnessApplicationStatus.UNAVAILABLE -> "Unavailable"
}

internal fun HarnessApplicationStatus.tone(): HarnessStatusTone = when (this) {
    HarnessApplicationStatus.AUTHORIZED -> HarnessStatusTone.SUCCESS
    HarnessApplicationStatus.PENDING -> HarnessStatusTone.INFO
    HarnessApplicationStatus.DISABLED -> HarnessStatusTone.NEUTRAL
    HarnessApplicationStatus.IDENTITY_CHANGED -> HarnessStatusTone.WARNING
    HarnessApplicationStatus.UNAVAILABLE -> HarnessStatusTone.ERROR
}

internal fun HarnessAssignmentStatus.label(): String = when (this) {
    HarnessAssignmentStatus.ACTIVE -> "Active"
    HarnessAssignmentStatus.DISABLED -> "Disabled"
    HarnessAssignmentStatus.SETUP_REQUIRED -> "Setup required"
    HarnessAssignmentStatus.UNAVAILABLE -> "Unavailable"
}

internal fun HarnessAssignmentStatus.tone(): HarnessStatusTone = when (this) {
    HarnessAssignmentStatus.ACTIVE -> HarnessStatusTone.SUCCESS
    HarnessAssignmentStatus.DISABLED -> HarnessStatusTone.NEUTRAL
    HarnessAssignmentStatus.SETUP_REQUIRED -> HarnessStatusTone.WARNING
    HarnessAssignmentStatus.UNAVAILABLE -> HarnessStatusTone.ERROR
}

internal fun assignmentCountLabel(count: Int): String = when (count) {
    1 -> "1 assigned use case"
    else -> "$count assigned use cases"
}
