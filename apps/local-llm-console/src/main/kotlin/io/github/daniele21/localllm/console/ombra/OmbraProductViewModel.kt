package io.github.daniele21.localllm.console.ombra

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Configuration-stable, process-local OMBRA owner. No SavedStateHandle is used intentionally:
 * Android process recreation starts a new private task instead of restoring document or PII data.
 */
internal class OmbraProductViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = OmbraProductController(application)
    internal val definitionActions = OmbraDefinitionSelectionController()
    internal val reviewActions =
        OmbraReviewProjectionController(
            snapshotProvider = controller::taskSnapshot,
            setDecision = controller::setDecision,
        )

    val workflow = controller.workflow
    val connection = controller.connection
    val definitions: StateFlow<OmbraDefinitionSelectionState> = definitionActions.state
    val review: StateFlow<OmbraReviewUiState> = reviewActions.state

    init {
        controller.connectHarness()
    }

    fun createOpenDocumentIntent(): Intent = controller.createOpenDocumentIntent()

    fun importPickedDocument(uri: Uri?): Boolean {
        reviewActions.clear()
        definitionActions.reset()
        return controller.importPickedDocument(uri)
    }

    fun startAnalysis(): Boolean = controller.setDefinitionsAndStartAnalysis(definitionActions.selectedDefinitions)

    fun taskSnapshot(): OmbraSensitiveTaskSnapshot = controller.taskSnapshot()

    fun exportTo(uri: Uri?): Boolean {
        if (!reviewActions.canExport()) return false
        val started = controller.startExport(uri)
        if (started) reviewActions.clear()
        return started
    }

    fun cancel(): Boolean = controller.cancel()

    fun retry(): Boolean = controller.retry()

    fun returnToReview(): Boolean = controller.returnToReview()

    fun reset(): Boolean {
        reviewActions.clear()
        definitionActions.reset()
        return controller.reset()
    }

    override fun onCleared() {
        reviewActions.clear()
        controller.close()
    }
}
