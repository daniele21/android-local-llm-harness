@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OmbraDocumentPickerSurface(
    title: String,
    description: String,
    actionLabel: String,
    modifier: Modifier = Modifier,
    selectedDocumentLabel: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = OmbraLayoutTokens.DocumentPickerMinHeight)
            .selectable(
                selected = selectedDocumentLabel != null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.large,
        color = if (selectedDocumentLabel == null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
        contentColor =
        if (selectedDocumentLabel == null) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(LocalOmbraSpacing.current.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.xs, Alignment.CenterVertically),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = selectedDocumentLabel ?: description,
                style = MaterialTheme.typography.bodyMedium,
                color =
                if (selectedDocumentLabel == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                textAlign = TextAlign.Center,
            )
            Text(
                text = actionLabel,
                modifier = Modifier.heightIn(min = LocalOmbraSpacing.current.minimumTouchTarget),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
