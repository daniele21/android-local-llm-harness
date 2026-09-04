package io.github.daniele21.localllm.console.ombra

import io.github.daniele21.localllm.console.pii.OmbraBuiltInPiiDefinitions
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionCreationResult
import io.github.daniele21.localllm.console.pii.PiiDefinitionDraft
import io.github.daniele21.localllm.console.pii.PiiDefinitionFactory
import io.github.daniele21.localllm.console.pii.PiiDefinitionValidation
import io.github.daniele21.localllm.console.pii.PiiTypeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class OmbraDefinitionSelectionState(
    val definitions: List<PiiDefinition> = OmbraBuiltInPiiDefinitions.all,
    val selectedIds: Set<PiiTypeId> = emptySet(),
) {
    val selectedDefinitions: List<PiiDefinition>
        get() = definitions.filter { it.id in selectedIds }
}

internal class OmbraDefinitionSelectionController {
    private val stateMutable = MutableStateFlow(OmbraDefinitionSelectionState())

    val state: StateFlow<OmbraDefinitionSelectionState> = stateMutable.asStateFlow()
    val selectedDefinitions: List<PiiDefinition>
        get() = stateMutable.value.selectedDefinitions

    fun toggle(id: PiiTypeId, selected: Boolean) {
        val current = stateMutable.value
        if (current.definitions.none { it.id == id }) return
        stateMutable.value =
            current.copy(
                selectedIds =
                if (selected) {
                    current.selectedIds + id
                } else {
                    current.selectedIds - id
                },
            )
    }

    fun addCustom(draft: PiiDefinitionDraft): PiiDefinitionValidation? {
        val current = stateMutable.value
        return when (val creation = PiiDefinitionFactory.createCustom(draft, current.definitions)) {
            is PiiDefinitionCreationResult.Created -> {
                stateMutable.value =
                    current.copy(
                        definitions = current.definitions + creation.definition,
                        selectedIds = current.selectedIds + creation.definition.id,
                    )
                null
            }

            is PiiDefinitionCreationResult.Invalid -> creation.validation
        }
    }

    fun reset() {
        stateMutable.value = OmbraDefinitionSelectionState()
    }
}
