@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class OmbraProgressState {
    PENDING,
    ACTIVE,
    COMPLETE,
    ERROR,
}

@Composable
fun OmbraStatusBadge(text: String, tone: OmbraStatusTone, modifier: Modifier = Modifier) {
    val colors = LocalOmbraStatusColors.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.container(tone),
        contentColor = colors.content(tone),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = LocalOmbraSpacing.current.sm, vertical = LocalOmbraSpacing.current.xs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun OmbraTaskProgressStep(title: String, state: OmbraProgressState, detail: String? = null, modifier: Modifier = Modifier) {
    val tone =
        when (state) {
            OmbraProgressState.PENDING -> OmbraStatusTone.NEUTRAL
            OmbraProgressState.ACTIVE -> OmbraStatusTone.LOCAL_READY
            OmbraProgressState.COMPLETE -> OmbraStatusTone.LOCAL_READY
            OmbraProgressState.ERROR -> OmbraStatusTone.ERROR
        }
    val stateMarker =
        when (state) {
            OmbraProgressState.PENDING -> "—"
            OmbraProgressState.ACTIVE -> "•"
            OmbraProgressState.COMPLETE -> "✓"
            OmbraProgressState.ERROR -> "!"
        }
    val colors = LocalOmbraStatusColors.current

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = LocalOmbraSpacing.current.minimumTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stateMarker,
            modifier =
                Modifier.widthIn(min = 28.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.container(tone))
                    .padding(horizontal = LocalOmbraSpacing.current.xs, vertical = LocalOmbraSpacing.current.xxs),
            color = colors.content(tone),
            style = MaterialTheme.typography.labelLarge,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.xxs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun OmbraDefinitionSelectionRow(
    label: String,
    definition: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onSelectedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = LocalOmbraSpacing.current.minimumTouchTarget)
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onSelectedChange,
                ).padding(vertical = LocalOmbraSpacing.current.sm),
        horizontalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = selected,
            enabled = enabled,
            onCheckedChange = null,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.xxs),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = definition,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OmbraReviewBanner(title: String, detail: String, tone: OmbraStatusTone = OmbraStatusTone.REVIEW, modifier: Modifier = Modifier) {
    val colors = LocalOmbraStatusColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.container(tone),
        contentColor = colors.content(tone),
    ) {
        Column(
            modifier = Modifier.padding(LocalOmbraSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Renders only the safe placeholder label. Callers must never pass the original sensitive value.
 * The accessibility description is explicit and caller-localized so hidden PII never enters semantics.
 */
@Composable
fun OmbraRedactionPlaceholder(placeholder: String, hiddenContentDescription: String, modifier: Modifier = Modifier) {
    Text(
        text = placeholder,
        modifier =
            modifier.clearAndSetSemantics { contentDescription = hiddenContentDescription }
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = LocalOmbraSpacing.current.xs, vertical = LocalOmbraSpacing.current.xxs),
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = OmbraFontFamilies.Placeholder),
    )
}

@Composable
fun OmbraExportSummary(acceptedLabel: String, ignoredLabel: String, pagesLabel: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(LocalOmbraSpacing.current.md),
            horizontalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.lg),
        ) {
            SummaryItem(acceptedLabel, Modifier.weight(1f))
            SummaryItem(ignoredLabel, Modifier.weight(1f))
            SummaryItem(pagesLabel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryItem(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
