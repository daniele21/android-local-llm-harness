@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.ui.designsystem.HarnessEmptyState
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

private data class HarnessCreatePresetDraft(
    val displayName: String,
    val selectedBase: HarnessPresetSummary,
    val contextText: String,
    val automaticModelSelection: Boolean,
    val parsedContext: Int?,
    val contextValid: Boolean,
)

private data class HarnessCreatePresetStatus(
    val saving: Boolean,
    val customSaved: HarnessApplicationsMutationState.Saved?,
)

private data class HarnessCreatePresetDraftActions(
    val onDisplayNameChanged: (String) -> Unit,
    val onBaseSelected: (HarnessPresetSummary) -> Unit,
    val onAutomaticModelSelectionChanged: (Boolean) -> Unit,
    val onContextChanged: (String) -> Unit,
)

@Composable
internal fun HarnessCreatePresetScreen(
    application: HarnessApplicationSummary?,
    assignment: HarnessAssignmentSummary?,
    mutationState: HarnessApplicationsMutationState,
    actions: HarnessCreatePresetActions,
    modifier: Modifier = Modifier,
) {
    if (application == null || assignment == null) {
        HarnessErrorState(
            title = "Preset creation unavailable",
            detail = "The application assignment may have changed. Return to the assigned use case and reload.",
            modifier = modifier,
        )
        return
    }
    val initialBase = assignment.defaultPreset ?: assignment.availablePresets.firstOrNull()
    if (initialBase == null) {
        HarnessEmptyState(
            title = "No base preset available",
            detail = "A published preset must be available before a Custom preset can be created.",
            modifier = modifier,
        )
        return
    }
    HarnessCreatePresetReadyContent(
        application = application,
        assignment = assignment,
        initialBase = initialBase,
        mutationState = mutationState,
        actions = actions,
        modifier = modifier,
    )
}

@Composable
private fun HarnessCreatePresetReadyContent(
    application: HarnessApplicationSummary,
    assignment: HarnessAssignmentSummary,
    initialBase: HarnessPresetSummary,
    mutationState: HarnessApplicationsMutationState,
    actions: HarnessCreatePresetActions,
    modifier: Modifier,
) {
    var displayName by rememberSaveable(application.applicationId, assignment.useCaseId) { mutableStateOf("") }
    var selectedBaseKey by rememberSaveable(application.applicationId, assignment.useCaseId) {
        mutableStateOf(initialBase.identityKey())
    }
    var contextText by rememberSaveable(application.applicationId, assignment.useCaseId) {
        mutableStateOf(initialBase.contextTokens?.toString().orEmpty())
    }
    var automaticModelSelection by rememberSaveable(application.applicationId, assignment.useCaseId) {
        mutableStateOf(initialBase.modelProfileId == null)
    }

    val selectedBase = assignment.availablePresets.firstOrNull { it.identityKey() == selectedBaseKey } ?: initialBase
    LaunchedEffect(assignment.bindingRevision, selectedBaseKey) {
        if (assignment.availablePresets.none { it.identityKey() == selectedBaseKey }) {
            selectedBaseKey = initialBase.identityKey()
            contextText = initialBase.contextTokens?.toString().orEmpty()
            automaticModelSelection = initialBase.modelProfileId == null
        }
    }

    val parsedContext = contextText.toIntOrNull()
    val saved = mutationState as? HarnessApplicationsMutationState.Saved
    val draft = HarnessCreatePresetDraft(
        displayName = displayName,
        selectedBase = selectedBase,
        contextText = contextText,
        automaticModelSelection = automaticModelSelection,
        parsedContext = parsedContext,
        contextValid = contextText.isBlank() || (parsedContext != null && parsedContext > 0),
    )
    val status = HarnessCreatePresetStatus(
        saving = mutationState == HarnessApplicationsMutationState.Saving,
        customSaved = saved?.takeIf { it.presetId != null && it.presetRevision != null },
    )
    val draftActions = HarnessCreatePresetDraftActions(
        onDisplayNameChanged = { displayName = it },
        onBaseSelected = { preset ->
            selectedBaseKey = preset.identityKey()
            contextText = preset.contextTokens?.toString().orEmpty()
            automaticModelSelection = preset.modelProfileId == null
            actions.onClearFeedback()
        },
        onAutomaticModelSelectionChanged = {
            automaticModelSelection = it
            actions.onClearFeedback()
        },
        onContextChanged = { value ->
            if (value.isEmpty() || value.all(Char::isDigit)) {
                contextText = value
                actions.onClearFeedback()
            }
        },
    )
    HarnessCreatePresetForm(
        assignment = assignment,
        mutationState = mutationState,
        draft = draft,
        status = status,
        actions = actions,
        draftActions = draftActions,
        modifier = modifier,
    )
}

@Composable
private fun HarnessCreatePresetForm(
    assignment: HarnessAssignmentSummary,
    mutationState: HarnessApplicationsMutationState,
    draft: HarnessCreatePresetDraft,
    status: HarnessCreatePresetStatus,
    actions: HarnessCreatePresetActions,
    draftActions: HarnessCreatePresetDraftActions,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("create-custom-preset"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item { HarnessCreatePresetHeader(assignment.displayName) }
        item {
            HarnessPresetNameField(
                value = draft.displayName,
                enabled = !status.saving && status.customSaved == null,
                onValueChange = draftActions.onDisplayNameChanged,
            )
        }
        item { Text("Start from", style = MaterialTheme.typography.titleMedium) }
        items(items = assignment.availablePresets, key = HarnessPresetSummary::identityKey) { preset ->
            HarnessPresetBaseCard(
                preset = preset,
                selected = preset.identityKey() == draft.selectedBase.identityKey(),
                enabled = !status.saving && status.customSaved == null,
                onSelect = { draftActions.onBaseSelected(preset) },
            )
        }
        item {
            HarnessModelPolicyCard(
                selectedBase = draft.selectedBase,
                automaticModelSelection = draft.automaticModelSelection,
                enabled = !status.saving && status.customSaved == null,
                onAutomaticModelSelectionChanged = draftActions.onAutomaticModelSelectionChanged,
            )
        }
        item {
            HarnessContextTokensField(
                value = draft.contextText,
                valid = draft.contextValid,
                enabled = !status.saving && status.customSaved == null,
                onValueChange = draftActions.onContextChanged,
            )
        }
        item { HarnessCustomPresetFeedback(state = mutationState, actions = actions) }
        if (status.customSaved == null) {
            item {
                HarnessSavePresetButton(
                    saving = status.saving,
                    enabled = !status.saving && draft.displayName.isNotBlank() && draft.contextValid,
                    onSave = {
                        actions.onSave(
                            draft.selectedBase,
                            draft.displayName.trim(),
                            draft.automaticModelSelection,
                            draft.parsedContext,
                        )
                    },
                )
            }
        }
    }
}

private fun HarnessPresetSummary.identityKey(): String = "$presetId:$revision"
