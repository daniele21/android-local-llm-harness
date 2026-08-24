@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessColors
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

@Composable
internal fun PerformanceScreen(
    state: PerformanceState,
    modelOptions: List<PerformanceModelOption>,
    profileOptions: List<PerformanceExecutionProfileOption>,
    runnerAvailable: Boolean,
    onIntent: (PerformanceIntent) -> Unit,
    onOpenModels: () -> Unit,
) {
    HarnessScreenList(title = null) {
        item {
            Text(
                "Evidence-backed model evaluation",
                style = MaterialTheme.typography.labelLarge,
                color = HarnessColors.Secondary,
            )
        }
        item { PerformanceSectionSelector(state.selectedSection, onIntent) }
        item {
            when (state.selectedSection) {
                PerformanceSection.RUN -> PerformanceRunSection(
                    state = state,
                    modelOptions = modelOptions,
                    profileOptions = profileOptions,
                    runnerAvailable = runnerAvailable,
                    onIntent = onIntent,
                    onOpenModels = onOpenModels,
                )

                PerformanceSection.DATASETS -> PerformanceCollectionSection(
                    title = "Datasets",
                    state = performanceDatasetSurfaceState(state.datasets),
                    emptyMessage = "No evaluation datasets are installed yet.",
                    availableLabel = "installed datasets",
                )

                PerformanceSection.HISTORY -> PerformanceCollectionSection(
                    title = "History",
                    state = performanceHistorySurfaceState(state.history),
                    emptyMessage = "No completed evaluation runs are available yet.",
                    availableLabel = "evaluation runs",
                )

                PerformanceSection.COMPARE -> PerformanceCompareSection(state)
            }
        }
    }
}

@Composable
private fun PerformanceSectionSelector(
    selected: PerformanceSection,
    onIntent: (PerformanceIntent) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("performance-sections"),
    ) {
        items(PerformanceSection.entries) { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onIntent(PerformanceIntent.SelectSection(section)) },
                label = { Text(performanceSectionLabel(section)) },
                modifier = Modifier.testTag("performance-section-${section.name.lowercase()}"),
            )
        }
    }
}

private fun performanceSectionLabel(section: PerformanceSection): String = when (section) {
    PerformanceSection.RUN -> "Run"
    PerformanceSection.DATASETS -> "Datasets"
    PerformanceSection.HISTORY -> "History"
    PerformanceSection.COMPARE -> "Compare"
}

@Composable
private fun PerformanceRunSection(
    state: PerformanceState,
    modelOptions: List<PerformanceModelOption>,
    profileOptions: List<PerformanceExecutionProfileOption>,
    runnerAvailable: Boolean,
    onIntent: (PerformanceIntent) -> Unit,
    onOpenModels: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PerformanceModelSelector(state, modelOptions, onIntent, onOpenModels)
        PerformanceDatasetSelectionState(state)
        PerformanceSampleSelectionState(state)
        PerformanceProfileSelector(state, profileOptions, onIntent)
        PerformanceReadinessCard(state, runnerAvailable, onIntent)
    }
}

@Composable
private fun PerformanceModelSelector(
    state: PerformanceState,
    options: List<PerformanceModelOption>,
    onIntent: (PerformanceIntent) -> Unit,
    onOpenModels: () -> Unit,
) {
    HarnessCard {
        Text("Model", style = MaterialTheme.typography.titleMedium)
        if (options.isEmpty()) {
            Text(
                "No installed product-supported model is available for evaluation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HarnessSecondaryButton(text = "Open Models", onClick = onOpenModels)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = state.runSetup.model == option.identity,
                        onClick = { onIntent(PerformanceIntent.SelectModel(option.identity)) },
                        label = { Text(option.displayName) },
                        modifier = Modifier.testTag("performance-model-${option.identity.artifactDigest.sha256.take(12)}"),
                    )
                    if (state.runSetup.model == option.identity) {
                        Text(
                            option.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceDatasetSelectionState(state: PerformanceState) {
    HarnessCard {
        Text("Dataset", style = MaterialTheme.typography.titleMedium)
        val selection = state.runSetup.dataset
        if (selection == null) {
            Text(
                "Dataset selection will become interactive when the connected dataset list is wired into this surface.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(selection.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${selection.caseCount} cases · ${selection.id.value} · ${selection.version.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerformanceSampleSelectionState(state: PerformanceState) {
    HarnessCard {
        Text("Samples", style = MaterialTheme.typography.titleMedium)
        Text(
            performanceSampleLabel(state.runSetup.sampleSelection),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Sampling stays deterministic and dataset-bounded; the dedicated selector is owned by the sampling UI slice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun performanceSampleLabel(selection: PerformanceSampleSelection): String = when (selection) {
    PerformanceSampleSelection.Smoke -> "Smoke · 20"
    PerformanceSampleSelection.Quick -> "Quick · 50"
    PerformanceSampleSelection.Standard -> "Standard · 100"
    PerformanceSampleSelection.Extended -> "Extended · 200"
    PerformanceSampleSelection.All -> "All cases"
    is PerformanceSampleSelection.Custom -> "Custom · ${selection.count}"
}

@Composable
private fun PerformanceProfileSelector(
    state: PerformanceState,
    options: List<PerformanceExecutionProfileOption>,
    onIntent: (PerformanceIntent) -> Unit,
) {
    HarnessCard {
        Text("Execution profile", style = MaterialTheme.typography.titleMedium)
        if (options.isEmpty()) {
            Text(
                "Execution profiles are unavailable until the evaluation runtime binding exposes compatible profile identities.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            options.forEach { option ->
                FilterChip(
                    selected = state.runSetup.executionProfile == option.ref,
                    onClick = { onIntent(PerformanceIntent.SelectExecutionProfile(option.ref)) },
                    enabled = option.compatible,
                    label = { Text("${option.label} · v${option.ref.version}") },
                )
                Text(
                    option.incompatibilityReason ?: option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PerformanceReadinessCard(
    state: PerformanceState,
    runnerAvailable: Boolean,
    onIntent: (PerformanceIntent) -> Unit,
) {
    val ready = state.runSetup.readiness == PerformanceRunReadiness.Ready
    HarnessCard(emphasized = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Readiness", style = MaterialTheme.typography.titleMedium)
            HarnessStatusBadge(
                text = when {
                    !runnerAvailable -> "Runner unavailable"
                    ready -> "Ready"
                    else -> "Setup incomplete"
                },
                tone = if (runnerAvailable && ready) HarnessStatusTone.SUCCESS else HarnessStatusTone.NEUTRAL,
            )
        }
        val detail = performanceReadinessDetail(state.runSetup.readiness, runnerAvailable)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HarnessPrimaryButton(
            text = "Start evaluation",
            enabled = runnerAvailable && ready && state.activeRun == null,
            modifier = Modifier.fillMaxWidth().testTag("performance-start"),
            onClick = { onIntent(PerformanceIntent.StartRun) },
        )
    }
}

private fun performanceReadinessDetail(
    readiness: PerformanceRunReadiness,
    runnerAvailable: Boolean,
): String {
    if (!runnerAvailable) {
        return "The connected evaluation runner is not available in this build path yet. Setup remains inspectable without claiming executable readiness."
    }
    return when (readiness) {
        PerformanceRunReadiness.Incomplete -> "Choose the required evaluation inputs."
        PerformanceRunReadiness.Ready -> "All required source-backed inputs are available."
        is PerformanceRunReadiness.Blocked -> readiness.reasons.joinToString(", ") { reason ->
            performanceBlockReasonLabel(reason)
        }
    }
}

private fun performanceBlockReasonLabel(reason: PerformanceBlockReason): String = when (reason) {
    PerformanceBlockReason.MODEL_REQUIRED -> "model required"
    PerformanceBlockReason.DATASET_REQUIRED -> "dataset required"
    PerformanceBlockReason.SAMPLE_SELECTION_UNAVAILABLE -> "sample selection unavailable"
    PerformanceBlockReason.EXECUTION_PROFILE_REQUIRED -> "execution profile required"
    PerformanceBlockReason.MODEL_UNAVAILABLE -> "model unavailable"
}

@Composable
private fun PerformanceCollectionSection(
    title: String,
    state: PerformanceSurfaceState,
    emptyMessage: String,
    availableLabel: String,
) {
    HarnessCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        when (state) {
            PerformanceSurfaceState.Loading -> Text("Loading…")
            is PerformanceSurfaceState.Failure -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
            )

            PerformanceSurfaceState.Empty -> Text(
                emptyMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is PerformanceSurfaceState.Available -> Text(
                "${state.count} $availableLabel available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerformanceCompareSection(state: PerformanceState) {
    val surface = performanceCompareSurfaceState(state)
    HarnessCard {
        Text("Compare", style = MaterialTheme.typography.titleLarge)
        when (surface) {
            PerformanceSurfaceState.Loading -> Text("Loading comparison candidates…")
            is PerformanceSurfaceState.Failure -> Text(surface.message, color = MaterialTheme.colorScheme.error)
            PerformanceSurfaceState.Empty -> Text(
                "Complete at least one evaluation run before comparison becomes available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is PerformanceSurfaceState.Available -> Text(
                "${surface.count} historical runs are available. Result compatibility and metric-specific comparison remain source-backed by the result/compare slices.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Selected for comparison: ${state.compare.selectedRunIds.size}/2",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
