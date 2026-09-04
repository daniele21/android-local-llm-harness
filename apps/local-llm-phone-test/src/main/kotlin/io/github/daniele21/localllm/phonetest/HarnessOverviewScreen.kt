@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessColors
import io.github.daniele21.localllm.ui.designsystem.HarnessMetric
import io.github.daniele21.localllm.ui.designsystem.HarnessMetricRow
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton

@Composable
internal fun HarnessOverviewScreen(
    state: HarnessUiState,
    diagnostics: DiagnosticsUiState,
    processPss: String?,
    thermalStatus: String?,
    onOpenPlayground: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val presentation = harnessOverviewPresentation(
        state = state,
        diagnostics = diagnostics,
        processPss = processPss,
        thermalStatus = thermalStatus,
    )
    val stackDenseContent = currentHarnessAdaptivePolicy().stackDenseContent

    HarnessScreenList(title = null) {
        item { OverviewPolicyLabel() }
        item {
            OverviewHeroCard(
                presentation = presentation,
                stackDenseContent = stackDenseContent,
                onOpenPlayground = onOpenPlayground,
                onOpenModels = onOpenModels,
            )
        }
        item { OverviewCurrentStatePanel(presentation, onOpenModels, onOpenPlayground, onOpenDiagnostics) }
        item {
            OverviewDeviceEvidencePanel(
                presentation = presentation,
                stackDenseContent = stackDenseContent,
                onOpenDiagnostics = onOpenDiagnostics,
            )
        }
        item { OverviewRecentActivityPanel(presentation, onOpenPlayground) }
    }
}

@Composable
private fun OverviewPolicyLabel() {
    Text(
        "Local inference only · no cloud fallback",
        style = MaterialTheme.typography.labelLarge,
        color = HarnessColors.Secondary,
    )
}

@Composable
private fun OverviewHeroCard(
    presentation: HarnessOverviewPresentation,
    stackDenseContent: Boolean,
    onOpenPlayground: () -> Unit,
    onOpenModels: () -> Unit,
) {
    HarnessCard(emphasized = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    presentation.heroLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = overviewHeroLabelColor(presentation.primaryAction),
                )
                Text(presentation.heroTitle, style = MaterialTheme.typography.headlineMedium)
                Text(
                    presentation.heroDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!stackDenseContent) {
                HarnessRuntimeGlyph(
                    ready = presentation.primaryAction == HarnessOverviewPrimaryAction.RUN_PROMPT,
                    modifier = Modifier.size(58.dp),
                )
            }
        }
        HarnessPrimaryButton(
            text = overviewPrimaryActionLabel(presentation.primaryAction),
            modifier = Modifier.fillMaxWidth(),
            onClick = overviewPrimaryActionClick(
                presentation.primaryAction,
                onOpenPlayground,
                onOpenModels,
            ),
        )
    }
}

@Composable
private fun overviewHeroLabelColor(primaryAction: HarnessOverviewPrimaryAction) =
    if (primaryAction == HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE) {
        HarnessColors.Warning
    } else {
        HarnessColors.Secondary
    }

@Composable
private fun OverviewCurrentStatePanel(
    presentation: HarnessOverviewPresentation,
    onOpenModels: () -> Unit,
    onOpenPlayground: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    OverviewSectionPanel("Current state") {
        OverviewInfoRow(
            icon = HarnessDestination.MODELS,
            label = "Selected model",
            value = presentation.selectedModelValue,
            status = presentation.selectedModelStatus,
            statusPositive = presentation.selectedModelPositive,
            onClick = onOpenModels,
        )
        OverviewInfoRow(
            icon = HarnessDestination.PLAYGROUND,
            label = "Runtime / residency",
            value = "${presentation.runtimeValue} · ${presentation.residencyValue}",
            status = presentation.residencyStatus,
            statusPositive = presentation.residencyPositive,
            onClick = onOpenPlayground,
        )
        OverviewInfoRow(
            icon = HarnessDestination.DIAGNOSTICS,
            label = "Runtime health",
            value = presentation.healthValue,
            status = presentation.healthStatus,
            statusPositive = presentation.healthPositive,
            onClick = onOpenDiagnostics,
            showDivider = false,
        )
    }
}

@Composable
private fun OverviewDeviceEvidencePanel(
    presentation: HarnessOverviewPresentation,
    stackDenseContent: Boolean,
    onOpenDiagnostics: () -> Unit,
) {
    OverviewSectionPanel("Device evidence") {
        if (!presentation.resourceEvidenceAvailable) {
            Text(
                "No resource snapshot has been captured yet. Memory and thermal state remain unavailable until an explicit capture runs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HarnessSecondaryButton(
                text = "Open diagnostics",
                onClick = onOpenDiagnostics,
            )
        } else {
            if (stackDenseContent) {
                HarnessMetric(
                    label = "Process PSS",
                    value = presentation.processPss,
                )
                HarnessMetric(
                    label = "Thermal",
                    value = presentation.thermalStatus,
                )
            } else {
                HarnessMetricRow {
                    HarnessMetric(
                        label = "Process PSS",
                        value = presentation.processPss,
                        modifier = Modifier.weight(1f),
                    )
                    HarnessMetric(
                        label = "Thermal",
                        value = presentation.thermalStatus,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Values come from the latest explicit resource snapshot. Missing measurements remain unavailable rather than being inferred.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverviewRecentActivityPanel(presentation: HarnessOverviewPresentation, onOpenPlayground: () -> Unit) {
    OverviewSectionPanel("Recent activity") {
        OverviewInfoRow(
            icon = HarnessDestination.PLAYGROUND,
            label = "Latest inference",
            value = presentation.latestRunValue,
            status = presentation.latestRunStatus,
            statusPositive = presentation.latestRunPositive,
            onClick = onOpenPlayground,
            showDivider = false,
        )
    }
}

@Composable
private fun OverviewSectionPanel(title: String, content: @Composable () -> Unit) {
    HarnessCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        content()
    }
}

@Composable
private fun OverviewInfoRow(
    icon: HarnessDestination,
    label: String,
    value: String,
    status: String,
    statusPositive: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HarnessMinimumTouchTarget)
            .clickable(
                onClickLabel = "Open $label",
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HarnessDestinationIcon(icon, selected = false, modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (label == "Runtime / residency") FontFamily.Monospace else null,
            )
        }
        Text(
            status,
            style = MaterialTheme.typography.labelLarge,
            color = if (statusPositive) HarnessColors.Secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }
}
