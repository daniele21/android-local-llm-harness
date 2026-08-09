@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessColors
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

@Composable
internal fun ModernPlaygroundResponseCard(presentation: PlaygroundPresentation) {
    val statusTone = presentation.statusTone.toHarnessStatusTone()
    val accent = presentation.statusTone.accentColor()
    val hasMetrics = listOf(presentation.ttft, presentation.total, presentation.decode).any { it != UNAVAILABLE_VALUE }
    val hasRuntimeDetails = presentation.stopReason != UNAVAILABLE_VALUE || presentation.effectiveConfiguration != null

    HarnessCard(
        emphasized = true,
        modifier = Modifier.testTag("playground-response-card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(
                        modifier = Modifier.size(34.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ">_",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text("Response", style = MaterialTheme.typography.titleLarge)
            }
            HarnessStatusBadge(
                label = presentation.statusLabel.removePrefix("●  "),
                tone = statusTone,
                modifier = Modifier.testTag("playground-response-status"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(7.dp).clip(CircleShape).background(accent),
            )
            Text(
                text = presentation.detail.ifBlank { presentation.statusTone.defaultDetail() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SelectionContainer {
            PlaygroundMarkdownResponse(
                source = presentation.responseText,
                placeholder = presentation.responseText == EMPTY_RESPONSE_VALUE,
            )
        }

        if (hasMetrics) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResponseMetricTile(
                    label = "TTFT",
                    value = presentation.ttft,
                    glyph = "◴",
                    modifier = Modifier.weight(1f),
                )
                ResponseMetricTile(
                    label = "Total",
                    value = presentation.total,
                    glyph = "◷",
                    modifier = Modifier.weight(1f),
                )
                ResponseMetricTile(
                    label = "Decode",
                    value = presentation.decode,
                    glyph = "≈",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (hasRuntimeDetails) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
            Text(
                text = "Run details",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RuntimeMetadataChips(presentation)
        }
    }
}

@Composable
private fun ResponseMetricTile(label: String, value: String, glyph: String, modifier: Modifier = Modifier) {
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
private fun RuntimeMetadataChips(presentation: PlaygroundPresentation) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (presentation.stopReason != UNAVAILABLE_VALUE) {
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

@Composable
private fun PlaygroundMarkdownResponse(source: String, placeholder: Boolean) {
    val blocks = PlaygroundMarkdownParser.parse(source)
    if (blocks.isEmpty()) {
        Text(
            text = source,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("playground-response-markdown"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is PlaygroundMarkdownBlock.Paragraph -> MarkdownInlineText(
                    inline = block.inline,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 27.sp,
                    ),
                    muted = placeholder,
                )

                is PlaygroundMarkdownBlock.Heading -> MarkdownInlineText(
                    inline = block.inline,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        else -> MaterialTheme.typography.titleLarge
                    },
                    muted = false,
                )

                is PlaygroundMarkdownBlock.ListItem -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = block.marker,
                        modifier = Modifier.width(if (block.ordered) 28.dp else 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontFamily = FontFamily.Monospace,
                    )
                    MarkdownInlineText(
                        inline = block.inline,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                        muted = false,
                        modifier = Modifier.weight(1f),
                    )
                }

                is PlaygroundMarkdownBlock.Quote -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier.width(3.dp).height(42.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    MarkdownInlineText(
                        inline = block.inline,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontStyle = FontStyle.Italic,
                            lineHeight = 25.sp,
                        ),
                        muted = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                is PlaygroundMarkdownBlock.Code -> MarkdownCodeBlock(block)
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    inline: List<PlaygroundMarkdownInline>,
    style: androidx.compose.ui.text.TextStyle,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val codeBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    val linkColor = MaterialTheme.colorScheme.secondary
    val annotated = buildAnnotatedString {
        inline.forEach { part ->
            val span = when (part.style) {
                PlaygroundMarkdownInlineStyle.PLAIN -> SpanStyle(color = primary)

                PlaygroundMarkdownInlineStyle.BOLD -> SpanStyle(color = primary, fontWeight = FontWeight.Bold)

                PlaygroundMarkdownInlineStyle.ITALIC -> SpanStyle(color = primary, fontStyle = FontStyle.Italic)

                PlaygroundMarkdownInlineStyle.BOLD_ITALIC -> SpanStyle(
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                )

                PlaygroundMarkdownInlineStyle.CODE -> SpanStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    background = codeBackground,
                    fontFamily = FontFamily.Monospace,
                )

                PlaygroundMarkdownInlineStyle.STRIKETHROUGH -> SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                )

                PlaygroundMarkdownInlineStyle.LINK -> SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium,
                )
            }
            withStyle(span) { append(part.text) }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
    )
}

@Composable
private fun MarkdownCodeBlock(block: PlaygroundMarkdownBlock.Code) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            block.language?.let { language ->
                Text(
                    text = language.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                text = block.text,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun PlaygroundPresentationTone.toHarnessStatusTone(): HarnessStatusTone = when (this) {
    PlaygroundPresentationTone.NEUTRAL -> HarnessStatusTone.NEUTRAL
    PlaygroundPresentationTone.ACTIVE -> HarnessStatusTone.INFO
    PlaygroundPresentationTone.SUCCESS -> HarnessStatusTone.SUCCESS
    PlaygroundPresentationTone.ERROR -> HarnessStatusTone.ERROR
    PlaygroundPresentationTone.WARNING -> HarnessStatusTone.WARNING
}

@Composable
private fun PlaygroundPresentationTone.accentColor(): Color = when (this) {
    PlaygroundPresentationTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    PlaygroundPresentationTone.ACTIVE -> MaterialTheme.colorScheme.primary
    PlaygroundPresentationTone.SUCCESS -> HarnessColors.Secondary
    PlaygroundPresentationTone.ERROR -> MaterialTheme.colorScheme.error
    PlaygroundPresentationTone.WARNING -> HarnessColors.Warning
}

private fun PlaygroundPresentationTone.defaultDetail(): String = when (this) {
    PlaygroundPresentationTone.NEUTRAL -> "Ready for a local generation"
    PlaygroundPresentationTone.ACTIVE -> "Local inference is running"
    PlaygroundPresentationTone.SUCCESS -> "Generation completed successfully"
    PlaygroundPresentationTone.ERROR -> "Generation failed"
    PlaygroundPresentationTone.WARNING -> "Generation stopped"
}

private const val UNAVAILABLE_VALUE = "Unavailable"
private const val EMPTY_RESPONSE_VALUE = "Generated output will appear here."
