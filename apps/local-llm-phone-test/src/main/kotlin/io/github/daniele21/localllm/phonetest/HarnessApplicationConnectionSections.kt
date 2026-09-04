@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessKeyValueRow
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

@Composable
internal fun ConnectionScreenHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall)) {
        Text("New app connection", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Authorize one exact Android package and signing identity to use a selected Harness use case.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ConnectionReviewCard(
    displayName: String,
    packageName: String,
    selectedUseCase: HarnessConnectionUseCaseOption,
    selectedPreset: HarnessConnectionPresetOption,
) {
    HarnessCard(emphasized = true) {
        Text("Review connection", style = MaterialTheme.typography.titleMedium)
        HarnessKeyValueRow("Application", displayName.ifBlank { "Not set" })
        HarnessKeyValueRow("Package", packageName.ifBlank { "Not set" })
        HarnessKeyValueRow("Use case", selectedUseCase.displayName)
        HarnessKeyValueRow("Default preset", selectedPreset.displayName)
        Text(
            "Creating this connection enables access immediately. You can disable it later without deleting its configuration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal data class ConnectionSubmission(
    val applicationId: String,
    val displayName: String,
    val packageName: String,
    val signerSha256: String,
    val useCaseId: String,
    val presetId: String,
    val presetRevision: Int,
)

@Composable
internal fun ConnectionCreateAction(
    saving: Boolean,
    enabled: Boolean,
    submission: ConnectionSubmission,
    onCreate: (String, String, String, String, String, String, Int) -> Unit,
) {
    HarnessPrimaryButton(
        text = if (saving) "Creating connection…" else "Create & enable connection",
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("connection-create"),
        onClick = {
            onCreate(
                submission.applicationId,
                submission.displayName,
                submission.packageName,
                submission.signerSha256,
                submission.useCaseId,
                submission.presetId,
                submission.presetRevision,
            )
        },
    )
}
