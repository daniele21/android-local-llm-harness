@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessMetric
import io.github.daniele21.localllm.ui.designsystem.HarnessMetricRow
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HarnessDetailTopBar(title: String, subtitle: String, onNavigateBack: () -> Unit) {
    TopAppBar(
        modifier = Modifier.testTag("harnessDetailTopBar"),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.semantics { contentDescription = "Back" },
            ) {
                HarnessDestinationIcon(
                    destination = HarnessDestination.OVERVIEW,
                    selected = false,
                    backArrow = true,
                )
            }
        },
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
internal fun PrivacyDetailScreen() {
    DetailList {
        item {
            HarnessCard(emphasized = true) {
                Text("Local by design", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Prompts, generated output, and GGUF model data remain on this device " +
                        "during normal Harness operation.",
                )
                HarnessStatusBadge("On-device", HarnessStatusTone.SUCCESS)
            }
        }
        item {
            DetailCard(
                title = "Telemetry boundary",
                detail =
                "Normal telemetry stores technical lifecycle and performance data. " +
                    "Prompt and generated-output content are excluded.",
            )
        }
        item {
            DetailCard(
                title = "Model storage",
                detail =
                "Imported GGUF artifacts are verified and stored in the application's " +
                    "private content-addressed model store.",
            )
        }
        item {
            DetailCard(
                title = "No cloud inference",
                detail =
                "The connected Playground executes through the embedded llama.cpp runtime " +
                    "without sending inference content to a cloud model.",
            )
        }
    }
}

@Composable
internal fun StorageDetailScreen(importedModel: ImportedPhoneModel?, onOpenModels: () -> Unit) {
    DetailList {
        item {
            HarnessCard(emphasized = true) {
                Text("Local model storage", style = MaterialTheme.typography.titleLarge)
                Text(
                    importedModel?.let {
                        "A GGUF model is selected for this application. This view does not infer total installed storage."
                    } ?: "No GGUF model is currently selected.",
                )
                HarnessMetricRow {
                    HarnessMetric(
                        label = "Selected model",
                        value = importedModel?.fileName ?: "None",
                        modifier = Modifier.weight(1f),
                    )
                    HarnessMetric(
                        label = "Selected size",
                        value = importedModel?.sizeBytes?.let(::formatDetailBytes) ?: "Unavailable",
                        modifier = Modifier.weight(1f),
                    )
                }
                HarnessPrimaryButton("Manage models", onClick = onOpenModels)
            }
        }
        item {
            DetailCard(
                title = "Storage accounting",
                detail =
                "This surface reports only source-backed selected-model size. " +
                    "It does not derive total installed storage from selection state.",
            )
        }
        item {
            DetailCard(
                title = "App-private storage",
                detail = "Model paths are not exposed in the product UI or persisted in catalog metadata.",
            )
        }
        item {
            DetailCard(
                title = "Explicit removal",
                detail =
                "Removing a model requires confirmation and is blocked while the runtime " +
                    "still owns the loaded artifact.",
            )
        }
    }
}

@Composable
internal fun BuildDetailScreen(versionName: String, versionCode: String, applicationId: String) {
    DetailList {
        item {
            HarnessCard(emphasized = true) {
                Text("Harness", style = MaterialTheme.typography.titleLarge)
                Text("Local AI Console")
                HarnessMetricRow {
                    HarnessMetric("Version", versionName, Modifier.weight(1f))
                    HarnessMetric("Build", versionCode, Modifier.weight(1f))
                }
            }
        }
        item {
            DetailCard("Application ID", applicationId, monospace = true)
        }
        item {
            DetailCard("Runtime", "Embedded llama.cpp · GGUF · arm64-v8a")
        }
        item {
            DetailCard(
                "Execution boundary",
                "This release uses the in-process HarnessRuntimeGraph. " +
                    "Binder/AIDL sharing is not enabled in this phase.",
            )
        }
    }
}

@Composable
internal fun DeveloperToolsDetailScreen(onOpenHealth: () -> Unit, onOpenLogs: () -> Unit, onOpenPhysicalValidation: () -> Unit) {
    DetailList {
        item {
            HarnessCard(emphasized = true) {
                Text("Runtime diagnostics", style = MaterialTheme.typography.titleLarge)
                Text("Inspect explicit, privacy-safe evidence from the embedded runtime.")
                HarnessPrimaryButton("Open health checks", onClick = onOpenHealth)
                HarnessSecondaryButton("Open structured logs", onClick = onOpenLogs)
            }
        }
        item {
            HarnessCard {
                Text("Physical-device gate", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Run generation, cancellation, and repeated lifecycle checks with a real GGUF " +
                        "on representative arm64 hardware.",
                )
                HarnessSecondaryButton("Open physical validation", onClick = onOpenPhysicalValidation)
            }
        }
    }
}

@Composable
internal fun PhysicalValidationDetailScreen(
    modelAvailable: Boolean,
    busy: Boolean,
    latestReport: String,
    onRunValidation: () -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
) {
    DetailList {
        item {
            HarnessCard(emphasized = true) {
                Text("Representative device evidence", style = MaterialTheme.typography.titleLarge)
                Text("Runs generation, cancellation, and repeated load/generate/unload memory cycles.")
                HarnessStatusBadge(
                    label = if (modelAvailable) "Model ready" else "Model required",
                    tone = if (modelAvailable) HarnessStatusTone.SUCCESS else HarnessStatusTone.WARNING,
                )
                HarnessPrimaryButton(
                    text = "Run full validation",
                    enabled = modelAvailable && !busy,
                    onClick = onRunValidation,
                )
            }
        }
        item {
            HarnessCard {
                Text("Privacy-safe report", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(
                        latestReport.ifBlank { "No validation report yet." },
                        fontFamily = FontFamily.Monospace,
                    )
                }
                HarnessSecondaryButton(
                    text = "Copy report",
                    enabled = latestReport.isNotBlank(),
                    onClick = onCopyReport,
                )
                HarnessSecondaryButton(
                    text = "Share report",
                    enabled = latestReport.isNotBlank(),
                    onClick = onShareReport,
                )
            }
        }
    }
}

@Composable
internal fun RequestTimelineDetailScreen(timeline: DiagnosticsRequestTimelineUi?, onCopyLog: (DiagnosticsLogUi) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("requestTimelineDetail"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HarnessCard(emphasized = true) {
                Text("Correlated request", style = MaterialTheme.typography.titleLarge)
                when {
                    timeline == null -> Text("Loading privacy-safe request evidence…")

                    timeline.sourceError != null -> Text(timeline.sourceError)

                    else -> {
                        HarnessMetricRow {
                            HarnessMetric("Request", timeline.requestIdPrefix, Modifier.weight(1f))
                            HarnessMetric("Run status", timeline.runStatus, Modifier.weight(1f))
                        }
                        Text(
                            if (timeline.events.isEmpty()) {
                                "No correlated structured events were recorded for this request."
                            } else {
                                "${timeline.events.size} events ordered chronologically."
                            },
                        )
                    }
                }
            }
        }
        items(
            items = timeline?.events.orEmpty(),
            key = { it.stableId },
        ) { log ->
            HarnessCard {
                Text("${log.level} · ${log.event}", style = MaterialTheme.typography.titleMedium)
                HarnessMetricRow {
                    HarnessMetric("Component", log.component, Modifier.weight(1f))
                    HarnessMetric(
                        "Offset",
                        log.offsetMs?.let { if (it >= 0) "+$it ms" else "$it ms" } ?: "Unavailable",
                        Modifier.weight(1f),
                    )
                }
                SelectionContainer {
                    Text(
                        log.fields.joinToString(separator = "\n") { "${it.name}=${it.value}" }
                            .ifEmpty { "No safe fields" },
                        fontFamily = FontFamily.Monospace,
                    )
                }
                HarnessSecondaryButton("Copy log entry") { onCopyLog(log) }
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, detail: String, monospace: Boolean = false) {
    HarnessCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun DetailList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

private fun formatDetailBytes(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
