@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HarnessCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LocalHarnessSpacing.current.large),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border =
            BorderStroke(
                width = HarnessBorderWidth,
                color = MaterialTheme.colorScheme.outline,
            ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
            content = content,
        )
    }
}

@Composable
fun HarnessMetricRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
        content = content,
    )
}

@Composable
fun HarnessMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = HarnessFontFamilies.Monospace,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun HarnessStatusBadge(label: String, tone: HarnessStatusTone, modifier: Modifier = Modifier) {
    val colors = LocalHarnessStatusColors.current
    Surface(
        modifier = modifier,
        color = colors.container(tone),
        contentColor = colors.content(tone),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(
                    horizontal = LocalHarnessSpacing.current.medium,
                    vertical = LocalHarnessSpacing.current.small,
                ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val HarnessBorderWidth = 1.dp
