@file:Suppress("FunctionName", "LongParameterList")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

enum class OmbraFindingDecision {
    UNDECIDED,
    ACCEPTED,
    IGNORED,
}

sealed interface OmbraFindingDisplayValue {
    val stateLabel: String

    @Immutable
    data class Hidden(val placeholder: String, override val stateLabel: String) : OmbraFindingDisplayValue {
        init {
            require(SafePlaceholderPattern.matches(placeholder)) {
                "Hidden findings require a safe placeholder such as [EMAIL_1]"
            }
        }

        override fun toString(): String = "Hidden(placeholder=$placeholder, stateLabel=<redacted>)"
    }

    @Immutable
    data class Revealed(val value: String, override val stateLabel: String) : OmbraFindingDisplayValue {
        override fun toString(): String = "Revealed(value=<redacted>, stateLabel=<redacted>)"
    }
}

private val SafePlaceholderPattern = Regex("\\[[A-Z][A-Z0-9_]{0,47}]")

internal fun ombraFindingAccessibilitySummary(
    category: String,
    positionLabel: String,
    value: OmbraFindingDisplayValue,
    decisionLabel: String,
): String = buildList {
    add(category)
    add(positionLabel)
    add(value.stateLabel)
    if (value is OmbraFindingDisplayValue.Revealed) add(value.value)
    add(decisionLabel)
}.joinToString(separator = ". ")

@Composable
fun OmbraFindingInspector(
    category: String,
    positionLabel: String,
    value: OmbraFindingDisplayValue,
    decision: OmbraFindingDecision,
    decisionLabel: String,
    acceptLabel: String,
    ignoreLabel: String,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onDecisionChange: (OmbraFindingDecision) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(LocalOmbraSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.md),
        ) {
            Column(
                modifier =
                Modifier.clearAndSetSemantics {
                    contentDescription =
                        ombraFindingAccessibilitySummary(
                            category = category,
                            positionLabel = positionLabel,
                            value = value,
                            decisionLabel = decisionLabel,
                        )
                },
                verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.xs),
            ) {
                Text(text = category, style = MaterialTheme.typography.titleMedium)
                Text(text = positionLabel, style = MaterialTheme.typography.labelMedium)
                when (value) {
                    is OmbraFindingDisplayValue.Hidden ->
                        OmbraRedactionPlaceholder(
                            placeholder = value.placeholder,
                            hiddenContentDescription = value.stateLabel,
                        )

                    is OmbraFindingDisplayValue.Revealed ->
                        Text(
                            text = value.value,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                }
            }
            OmbraDecisionControls(
                decision = decision,
                acceptLabel = acceptLabel,
                ignoreLabel = ignoreLabel,
                onDecisionChange = onDecisionChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.sm),
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = previousEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = LocalOmbraSpacing.current.minimumTouchTarget),
                ) {
                    Text(previousLabel)
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = nextEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = LocalOmbraSpacing.current.minimumTouchTarget),
                ) {
                    Text(nextLabel)
                }
            }
        }
    }
}

@Composable
fun OmbraDecisionControls(
    decision: OmbraFindingDecision,
    acceptLabel: String,
    ignoreLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDecisionChange: (OmbraFindingDecision) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.sm),
    ) {
        OmbraDecisionButton(
            label = acceptLabel,
            selected = decision == OmbraFindingDecision.ACCEPTED,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onDecisionChange(OmbraFindingDecision.ACCEPTED) },
        )
        OmbraDecisionButton(
            label = ignoreLabel,
            selected = decision == OmbraFindingDecision.IGNORED,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onDecisionChange(OmbraFindingDecision.IGNORED) },
        )
    }
}

@Composable
private fun OmbraDecisionButton(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val semanticsModifier =
        modifier
            .heightIn(min = LocalOmbraSpacing.current.minimumTouchTarget)
            .semantics { this.selected = selected }
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = semanticsModifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = semanticsModifier) { Text(label) }
    }
}
