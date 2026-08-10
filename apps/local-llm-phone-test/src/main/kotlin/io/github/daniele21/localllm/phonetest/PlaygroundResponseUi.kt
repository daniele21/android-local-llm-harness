@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge

@Composable
internal fun ModernPlaygroundResponseCard(presentation: PlaygroundPresentation) {
    val hasMetrics = listOf(
        presentation.ttft,
        presentation.timeToFirstAnswer,
        presentation.total,
        presentation.decode,
    ).any { it != UNAVAILABLE_VALUE }
    val hasRuntimeDetails = presentation.stopReason != UNAVAILABLE_VALUE || presentation.effectiveConfiguration != null

    HarnessCard(
        emphasized = true,
        modifier = Modifier.testTag("playground-response-card"),
    ) {
        ResponseHeader(presentation)
        ResponseStatusLine(presentation)
        if (presentation.reasoningText.isNotBlank()) {
            PlaygroundReasoningSection(presentation)
        }
        FinalAnswerSection(presentation)
        if (hasMetrics) ResponseMetrics(presentation)
        if (hasRuntimeDetails) ResponseRunDetails(presentation)
    }
}

@Composable
private fun ResponseHeader(presentation: PlaygroundPresentation) {
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
            tone = presentation.statusTone.toHarnessStatusTone(),
            modifier = Modifier.testTag("playground-response-status"),
        )
    }
}

@Composable
private fun ResponseStatusLine(presentation: PlaygroundPresentation) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(7.dp).clip(CircleShape).background(presentation.statusTone.accentColor()),
        )
        Text(
            text = presentation.detail.ifBlank { presentation.statusTone.defaultDetail() },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FinalAnswerSection(presentation: PlaygroundPresentation) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("playground-answer-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Final answer",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            PlaygroundMarkdownResponse(
                source = presentation.responseText,
                placeholder = presentation.responseText == EMPTY_RESPONSE_VALUE,
            )
        }
    }
}

@Composable
private fun ResponseMetrics(presentation: PlaygroundPresentation) {
    if (presentation.timeToFirstAnswer != UNAVAILABLE_VALUE) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaygroundResponseMetricTile(
                label = "TTFT",
                value = presentation.ttft,
                glyph = "◴",
                modifier = Modifier.weight(1f),
            )
            PlaygroundResponseMetricTile(
                label = "First answer",
                value = presentation.timeToFirstAnswer,
                glyph = "▶",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaygroundResponseMetricTile(
                label = "Total",
                value = presentation.total,
                glyph = "◷",
                modifier = Modifier.weight(1f),
            )
            PlaygroundResponseMetricTile(
                label = "Decode",
                value = presentation.decode,
                glyph = "≈",
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaygroundResponseMetricTile(
                label = "TTFT",
                value = presentation.ttft,
                glyph = "◴",
                modifier = Modifier.weight(1f),
            )
            PlaygroundResponseMetricTile(
                label = "Total",
                value = presentation.total,
                glyph = "◷",
                modifier = Modifier.weight(1f),
            )
            PlaygroundResponseMetricTile(
                label = "Decode",
                value = presentation.decode,
                glyph = "≈",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ResponseRunDetails(presentation: PlaygroundPresentation) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
    Text(
        text = "Run details",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PlaygroundRuntimeMetadataChips(presentation)
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
    style: TextStyle,
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

private const val UNAVAILABLE_VALUE = "Unavailable"
private const val EMPTY_RESPONSE_VALUE = "Generated output will appear here."
