@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

@Composable
fun HarnessKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    monospacedValue: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HarnessMinimumTouchTarget)
            .padding(vertical = LocalHarnessSpacing.current.small),
        horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (monospacedValue) HarnessFontFamilies.Monospace else FontFamily.Default,
        )
    }
}

@Composable
fun HarnessRecoveryCard(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    tone: HarnessStatusTone = HarnessStatusTone.WARNING,
) {
    HarnessCard(modifier = modifier, emphasized = true) {
        HarnessStatusBadge(label = title, tone = tone)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HarnessSecondaryButton(text = actionLabel, onClick = onAction)
    }
}
