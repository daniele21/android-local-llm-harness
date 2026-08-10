@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessColors
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

@Composable
internal fun PlaygroundResponseMetricTile(label: String, value: String, glyph: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.88f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    glyph,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun PlaygroundRuntimeMetadataChips(presentation: PlaygroundPresentation) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (presentation.stopReason != "Unavailable") {
            MetadataChip("Stop", presentation.stopReason, highlight = true)
        }
        presentation.effectiveConfiguration?.let { configuration ->
            MetadataChip("Context", configuration.contextSize.toString())
            MetadataChip("Prompt", configuration.promptTokenCount.toString())
            MetadataChip("Thinking", configuration.thinkingMode.name.lowercase())
            MetadataChip("Seed", configuration.effectiveSeed.toString())
            MetadataChip("Min-p", configuration.minP.toString())
            MetadataChip("Presence", configuration.presencePenalty.toString())
            MetadataChip("Repeat", "${configuration.repeatPenalty}/${configuration.repeatLastN}")
            MetadataChip(
                "Template",
                "${configuration.chatTemplateId} (${configuration.chatTemplateSource.name})",
            )
        }
    }
}

@Composable
private fun MetadataChip(label: String, value: String, highlight: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlight) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (highlight) {
                HarnessColors.Secondary.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.88f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlight) HarnessColors.Secondary else MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

internal fun PlaygroundPresentationTone.toHarnessStatusTone(): HarnessStatusTone = when (this) {
    PlaygroundPresentationTone.NEUTRAL -> HarnessStatusTone.NEUTRAL
    PlaygroundPresentationTone.ACTIVE -> HarnessStatusTone.INFO
    PlaygroundPresentationTone.SUCCESS -> HarnessStatusTone.SUCCESS
    PlaygroundPresentationTone.ERROR -> HarnessStatusTone.ERROR
    PlaygroundPresentationTone.WARNING -> HarnessStatusTone.WARNING
}

@Composable
internal fun PlaygroundPresentationTone.accentColor(): Color = when (this) {
    PlaygroundPresentationTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    PlaygroundPresentationTone.ACTIVE -> MaterialTheme.colorScheme.primary
    PlaygroundPresentationTone.SUCCESS -> HarnessColors.Secondary
    PlaygroundPresentationTone.ERROR -> MaterialTheme.colorScheme.error
    PlaygroundPresentationTone.WARNING -> HarnessColors.Warning
}

internal fun PlaygroundPresentationTone.defaultDetail(): String = when (this) {
    PlaygroundPresentationTone.NEUTRAL -> "Ready for a local generation"
    PlaygroundPresentationTone.ACTIVE -> "Local inference is running"
    PlaygroundPresentationTone.SUCCESS -> "Generation completed successfully"
    PlaygroundPresentationTone.ERROR -> "Generation failed"
    PlaygroundPresentationTone.WARNING -> "Generation stopped"
}
