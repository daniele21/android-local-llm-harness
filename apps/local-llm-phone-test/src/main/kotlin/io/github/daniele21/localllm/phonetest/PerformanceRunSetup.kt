@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

@Composable
internal fun PerformanceRunSection(
    state: PerformanceState,
    modelOptions: List<PerformanceModelOption>,
    profileOptions: List<PerformanceExecutionProfileOption>,
    runnerAvailable: Boolean,
    onIntent: (PerformanceIntent) -> Unit,
    onOpenModels: () -> Unit,
) {
    val stackDenseContent = currentHarnessAdaptivePolicy().stackDenseContent
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PerformanceDatasetSelector(state)
        PerformanceModelSelector(state, modelOptions, onIntent, onOpenModels)
        PerformanceSampleSelector(state, onIntent, stackDenseContent)
        PerformanceProfileSelector(state, profileOptions, onIntent)
        PerformanceReadinessCard(state, runnerAvailable, onIntent, stackDenseContent)
    }
}

@Composable
private fun PerformanceDatasetSelector(state: PerformanceState) {
    HarnessCard {
        Text("1 · Dataset", style = MaterialTheme.typography.titleMedium)
        val selection = state.runSetup.dataset
        if (selection != null) {
            Text(selection.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${selection.caseCount} cases · version ${selection.version.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@HarnessCard
        }
        when (val surface = performanceDatasetSurfaceState(state.datasets)) {
            PerformanceSurfaceState.Loading -> Text("Loading installed datasets…")

            is PerformanceSurfaceState.Failure -> Text(surface.message, color = MaterialTheme.colorScheme.error)

            PerformanceSurfaceState.Empty -> Text(
                "No evaluation dataset is currently available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is PerformanceSurfaceState.Available -> Text(
                "${surface.count} datasets are installed. Dataset selection is not available on this screen yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        Text("2 · Model", style = MaterialTheme.typography.titleMedium)
        if (options.isEmpty()) {
            Text(
                "No installed supported model is available for evaluation.",
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
                        modifier = Modifier.testTag(
                            "performance-model-${option.identity.artifactDigest.sha256.take(12)}",
                        ),
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
private fun PerformanceSampleSelector(
    state: PerformanceState,
    onIntent: (PerformanceIntent) -> Unit,
    stackDenseContent: Boolean,
) {
    HarnessCard {
        Text("3 · Samples", style = MaterialTheme.typography.titleMedium)
        Text(
            "Choose how many cases to evaluate. Presets larger than the selected dataset remain unavailable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (stackDenseContent) {
                performanceFixedSamples.forEach { selection ->
                    PerformanceSampleChip(state, selection, onIntent, Modifier.fillMaxWidth())
                }
            } else {
                performanceFixedSamples.chunked(2).forEach { rowSelections ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSelections.forEach { selection ->
                            PerformanceSampleChip(state, selection, onIntent)
                        }
                    }
                }
            }
        }
        if (state.runSetup.sampleSelection is PerformanceSampleSelection.Custom) {
            Text(
                performanceSampleLabel(state.runSetup.sampleSelection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerformanceSampleChip(
    state: PerformanceState,
    selection: PerformanceSampleSelection,
    onIntent: (PerformanceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = state.runSetup.sampleSelection == selection,
        enabled = performanceSampleEnabled(selection, state.runSetup.dataset?.caseCount),
        onClick = { onIntent(PerformanceIntent.SelectSample(selection)) },
        label = { Text(performanceSampleLabel(selection)) },
        modifier = modifier,
    )
}

@Composable
private fun PerformanceProfileSelector(
    state: PerformanceState,
    options: List<PerformanceExecutionProfileOption>,
    onIntent: (PerformanceIntent) -> Unit,
) {
    HarnessCard {
        Text("4 · Execution profile", style = MaterialTheme.typography.titleMedium)
        if (options.isEmpty()) {
            Text(
                "No compatible execution profile is available for selection yet. Evaluation stays disabled until one is available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
}

@Composable
private fun PerformanceReadinessCard(
    state: PerformanceState,
    runnerAvailable: Boolean,
    onIntent: (PerformanceIntent) -> Unit,
    stackDenseContent: Boolean,
) {
    val ready = state.runSetup.readiness == PerformanceRunReadiness.Ready
    val statusLabel = when {
        !runnerAvailable -> "Unavailable"
        ready -> "Ready"
        else -> "Setup incomplete"
    }
    val statusTone = if (runnerAvailable && ready) HarnessStatusTone.SUCCESS else HarnessStatusTone.NEUTRAL
    HarnessCard(emphasized = true) {
        if (stackDenseContent) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ready to run?", style = MaterialTheme.typography.titleMedium)
                HarnessStatusBadge(label = statusLabel, tone = statusTone)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Ready to run?", style = MaterialTheme.typography.titleMedium)
                HarnessStatusBadge(label = statusLabel, tone = statusTone)
            }
        }
        Text(
            performanceReadinessDetail(state.runSetup.readiness, runnerAvailable),
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
