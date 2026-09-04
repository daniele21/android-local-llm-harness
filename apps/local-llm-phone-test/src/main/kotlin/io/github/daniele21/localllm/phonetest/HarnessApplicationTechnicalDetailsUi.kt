@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessKeyValueRow
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

@Composable
internal fun HarnessApplicationTechnicalDetailsScreen(
    application: HarnessApplicationSummary?,
    assignment: HarnessAssignmentSummary?,
    preset: HarnessPresetSummary?,
    modifier: Modifier = Modifier,
) {
    if (application == null || assignment == null || preset == null) {
        HarnessErrorState(
            title = "Technical details unavailable",
            detail = "The application configuration may have changed. Reload the current assignment before reviewing identities.",
            modifier = modifier,
        )
        return
    }

    SelectionContainer {
        LazyColumn(
            modifier = modifier.fillMaxSize().testTag("application-technical-details"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LocalHarnessSpacing.current.large),
            verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
        ) {
            item {
                Text("Technical details", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                HarnessCard {
                    Text("Application", style = MaterialTheme.typography.titleMedium)
                    HarnessKeyValueRow("Application ID", application.applicationId, monospacedValue = true)
                    HarnessKeyValueRow("Package", application.packageName, monospacedValue = true)
                    HarnessKeyValueRow("Signer SHA-256", application.signerSha256, monospacedValue = true)
                    HarnessKeyValueRow("Registration state", application.status.technicalLabel())
                    HarnessKeyValueRow("First seen (epoch ms)", application.firstSeenAtEpochMs.toString(), monospacedValue = true)
                    HarnessKeyValueRow("Last seen (epoch ms)", application.lastSeenAtEpochMs.toString(), monospacedValue = true)
                }
            }
            item {
                HarnessCard {
                    Text("Assigned use case", style = MaterialTheme.typography.titleMedium)
                    HarnessKeyValueRow("Use-case ID", assignment.useCaseId, monospacedValue = true)
                    HarnessKeyValueRow("Use-case revision", assignment.useCaseRevision.toString(), monospacedValue = true)
                    HarnessKeyValueRow("Binding ID", assignment.bindingId, monospacedValue = true)
                    HarnessKeyValueRow("Binding revision", assignment.bindingRevision.toString(), monospacedValue = true)
                    HarnessKeyValueRow("Binding enabled", if (assignment.bindingEnabled) "Yes" else "No")
                }
            }
            item {
                HarnessCard {
                    Text("Preset", style = MaterialTheme.typography.titleMedium)
                    HarnessKeyValueRow("Preset ID", preset.presetId, monospacedValue = true)
                    HarnessKeyValueRow("Preset revision", preset.revision.toString(), monospacedValue = true)
                    HarnessKeyValueRow("Provenance", preset.originLabel())
                    HarnessKeyValueRow("Lifecycle", preset.lifecycleState.technicalLabel())
                    HarnessKeyValueRow("Default", if (preset.isDefault) "Yes" else "No")
                }
            }
        }
    }
}

private fun HarnessApplicationStatus.technicalLabel(): String = when (this) {
    HarnessApplicationStatus.AUTHORIZED -> "Authorized"
    HarnessApplicationStatus.PENDING -> "Pending"
    HarnessApplicationStatus.DISABLED -> "Disabled"
    HarnessApplicationStatus.IDENTITY_CHANGED -> "Identity changed"
    HarnessApplicationStatus.UNAVAILABLE -> "Unavailable"
}

private fun Enum<*>.technicalLabel(): String = name
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar { character -> character.uppercase() }
