@file:Suppress("FunctionName", "LongParameterList")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class OmbraDefinitionEditorState(
    val name: String,
    val definition: String,
    val example: String,
    val nameError: String? = null,
    val definitionError: String? = null,
    val exampleError: String? = null,
    val nameSupportingText: String? = null,
    val definitionSupportingText: String? = null,
    val exampleSupportingText: String? = null,
    val canAdd: Boolean = false,
) {
    override fun toString(): String = "OmbraDefinitionEditorState(" +
        "name=<redacted>, definition=<redacted>, example=<redacted>, " +
        "nameError=${nameError != null}, definitionError=${definitionError != null}, " +
        "exampleError=${exampleError != null}, nameSupportingText=${nameSupportingText != null}, " +
        "definitionSupportingText=${definitionSupportingText != null}, " +
        "exampleSupportingText=${exampleSupportingText != null}, canAdd=$canAdd)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmbraDefinitionEditorSheet(
    state: OmbraDefinitionEditorState,
    title: String,
    guidance: String,
    nameLabel: String,
    definitionLabel: String,
    exampleLabel: String,
    addLabel: String,
    cancelLabel: String,
    modifier: Modifier = Modifier,
    onNameChange: (String) -> Unit,
    onDefinitionChange: (String) -> Unit,
    onExampleChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = LocalOmbraSpacing.current.md, vertical = LocalOmbraSpacing.current.sm),
            verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.md),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = guidance,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OmbraDefinitionField(
                value = state.name,
                label = nameLabel,
                error = state.nameError,
                supportingText = state.nameSupportingText,
                onValueChange = onNameChange,
                singleLine = true,
            )
            OmbraDefinitionField(
                value = state.definition,
                label = definitionLabel,
                error = state.definitionError,
                supportingText = state.definitionSupportingText,
                onValueChange = onDefinitionChange,
                minLines = 3,
            )
            OmbraDefinitionField(
                value = state.example,
                label = exampleLabel,
                error = state.exampleError,
                supportingText = state.exampleSupportingText,
                onValueChange = onExampleChange,
                minLines = 2,
            )
            OmbraPrimaryButton(text = addLabel, enabled = state.canAdd, onClick = onAdd)
            OmbraSecondaryButton(text = cancelLabel, onClick = onDismiss)
        }
    }
}

@Composable
private fun OmbraDefinitionField(
    value: String,
    label: String,
    error: String?,
    supportingText: String?,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        supportingText = {
            (error ?: supportingText)?.let { Text(it) }
        },
        singleLine = singleLine,
        minLines = minLines,
    )
}
