package io.github.daniele21.localllm.console.ombra

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskSnapshot
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

/**
 * Configuration-stable, process-local OMBRA owner. No SavedStateHandle is used intentionally:
 * Android process recreation starts a new private task instead of restoring document or PII data.
 */
internal class OmbraProductViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = OmbraProductController(application)
    private val definitionsMutable = MutableStateFlow(OmbraDefinitionSelectionState())

    val workflow = controller.workflow
    val connection = controller.connection
    val definitions: StateFlow<OmbraDefinitionSelectionState> = definitionsMutable.asStateFlow()

    init {
        controller.connectHarness()
    }

    fun createOpenDocumentIntent(): Intent = controller.createOpenDocumentIntent()

    fun importPickedDocument(uri: Uri?): Boolean {
        definitionsMutable.value = OmbraDefinitionSelectionState()
        return controller.importPickedDocument(uri)
    }

    fun toggleDefinition(id: PiiTypeId, selected: Boolean) {
        val current = definitionsMutable.value
        if (current.definitions.none { it.id == id }) return
        definitionsMutable.value =
            current.copy(
                selectedIds =
                if (selected) {
                    current.selectedIds + id
                } else {
                    current.selectedIds - id
                },
            )
    }

    fun addCustomDefinition(draft: PiiDefinitionDraft): PiiDefinitionValidation? {
        val current = definitionsMutable.value
        return when (val creation = PiiDefinitionFactory.createCustom(draft, current.definitions)) {
            is PiiDefinitionCreationResult.Created -> {
                definitionsMutable.value =
                    current.copy(
                        definitions = current.definitions + creation.definition,
                        selectedIds = current.selectedIds + creation.definition.id,
                    )
                null
            }

            is PiiDefinitionCreationResult.Invalid -> creation.validation
        }
    }

    fun startAnalysis(): Boolean = controller.setDefinitionsAndStartAnalysis(definitionsMutable.value.selectedDefinitions)

    fun taskSnapshot(): OmbraSensitiveTaskSnapshot = controller.taskSnapshot()

    fun cancel(): Boolean = controller.cancel()

    fun retry(): Boolean = controller.retry()

    fun reset(): Boolean {
        definitionsMutable.value = OmbraDefinitionSelectionState()
        return controller.reset()
    }

    override fun onCleared() {
        controller.close()
    }
}
