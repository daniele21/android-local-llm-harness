@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Harnex components dark", showBackground = true)
@Composable
fun HarnessComponentsDarkPreview() {
    HarnessTheme(darkTheme = true) {
        HarnessComponentSheet()
    }
}

@Preview(name = "Harnex components light", showBackground = true)
@Composable
fun HarnessComponentsLightPreview() {
    HarnessTheme(darkTheme = false) {
        HarnessComponentSheet()
    }
}

@Composable
private fun HarnessComponentSheet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        HarnessTopAppBar(title = "Harnex", subtitle = "Local AI Console")
        HarnessCard {
            Text("Runtime", style = MaterialTheme.typography.titleLarge)
            HarnessMetricRow {
                HarnessMetric("Model", "Qwen 0.6B", Modifier.weight(1f))
                HarnessMetric("Speed", "13.8 tok/s", Modifier.weight(1f))
            }
            HarnessStatusBadge("Healthy", HarnessStatusTone.SUCCESS)
            HarnessPrimaryButton("Run locally") {}
            HarnessSecondaryButton("Import model") {}
        }
        HarnessEmptyState("No recent runs", "Completed requests will appear here.")
    }
}
