package io.github.daniele21.localllm.console.ombra

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.daniele21.localllm.console.analysis.OmbraBinderAnalysisComposition
import io.github.daniele21.localllm.console.application.InMemoryOmbraSensitiveTaskStore
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskSnapshot
import io.github.daniele21.localllm.console.document.AndroidOmbraDocumentExporter
import io.github.daniele21.localllm.console.document.AndroidOmbraDocumentExtractor
import io.github.daniele21.localllm.console.document.OmbraDocumentSourceRegistry
import io.github.daniele21.localllm.console.document.OmbraExportDestinationRegistry
import io.github.daniele21.localllm.console.document.OmbraPdfOpenDocumentCapability
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.presentation.OmbraApplicationOrchestrator
import io.github.daniele21.localllm.console.presentation.OmbraWorkflowState
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionObserver
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionSnapshot
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel-scoped OMBRA composition root. Sensitive task values remain process-local and are never
 * written to SavedState, intents, logs or workflow state.
 */
internal class OmbraProductController(context: Context) : AutoCloseable {
    private val sourceRegistry = OmbraDocumentSourceRegistry(context)
    private val destinationRegistry = OmbraExportDestinationRegistry(context)
    private val taskStore = InMemoryOmbraSensitiveTaskStore()
    private val extractor = AndroidOmbraDocumentExtractor(context, sourceRegistry)
    private val connectionMutable =
        MutableStateFlow(SharedRuntimeConnectionSnapshot(SharedRuntimeConnectionState.DISCONNECTED))
    private val analysis =
        OmbraBinderAnalysisComposition.create(
            context = context,
            observer = SharedRuntimeConnectionObserver { snapshot -> connectionMutable.value = snapshot },
        )
    private val exporter = AndroidOmbraDocumentExporter(context, destinationRegistry)
    private val workflowMutable = MutableStateFlow(OmbraWorkflowState())
    private val orchestrator =
        OmbraApplicationOrchestrator(
            extractor = extractor,
            analysisClient = analysis,
            exporter = exporter,
            taskStore = taskStore,
            sourceCapabilityCleanup = sourceRegistry,
            onStateChanged = { state -> workflowMutable.value = state },
        )
    private val openDocument = OmbraPdfOpenDocumentCapability(sourceRegistry)

    val workflow: StateFlow<OmbraWorkflowState> = workflowMutable.asStateFlow()
    val connection: StateFlow<SharedRuntimeConnectionSnapshot> = connectionMutable.asStateFlow()

    fun connectHarness() = analysis.connect()

    fun createOpenDocumentIntent(): Intent = openDocument.createIntent()

    fun importPickedDocument(uri: Uri?): Boolean {
        val sourceRef = runCatching { openDocument.registerResult(uri) }.getOrNull() ?: return false
        return orchestrator.startImport(sourceRef)
    }

    fun taskSnapshot(): OmbraSensitiveTaskSnapshot = orchestrator.task.snapshot()

    fun setDefinitionsAndStartAnalysis(definitions: Collection<PiiDefinition>): Boolean {
        if (connectionMutable.value.state != SharedRuntimeConnectionState.CONNECTED) return false
        if (!orchestrator.task.setDefinitions(definitions)) return false
        return orchestrator.startAnalysis()
    }

    fun cancel(): Boolean = orchestrator.cancel()

    fun retry(): Boolean = orchestrator.retry()

    fun reset(): Boolean = orchestrator.reset()

    override fun close() {
        taskStore.clear()
        sourceRegistry.clear()
        destinationRegistry.clear()
        analysis.close()
        extractor.close()
        exporter.close()
    }
}
