@file:Suppress("FunctionName", "LongMethod", "LongParameterList")

package io.github.daniele21.localllm.console.ombra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskSnapshot
import io.github.daniele21.localllm.console.pii.PiiDefinitionDraft
import io.github.daniele21.localllm.console.pii.PiiDefinitionValidation
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.console.presentation.OmbraFailureCode
import io.github.daniele21.localllm.console.presentation.OmbraOperationKind
import io.github.daniele21.localllm.console.presentation.OmbraRetryTarget
import io.github.daniele21.localllm.console.presentation.OmbraWorkflowStage
import io.github.daniele21.localllm.console.presentation.OmbraWorkflowState
import io.github.daniele21.localllm.ui.designsystem.LocalOmbraSpacing
import io.github.daniele21.localllm.ui.designsystem.OmbraDefinitionEditorSheet
import io.github.daniele21.localllm.ui.designsystem.OmbraDefinitionEditorState
import io.github.daniele21.localllm.ui.designsystem.OmbraDefinitionSelectionRow
import io.github.daniele21.localllm.ui.designsystem.OmbraDocumentPickerSurface
import io.github.daniele21.localllm.ui.designsystem.OmbraPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.OmbraProgressState
import io.github.daniele21.localllm.ui.designsystem.OmbraReviewBanner
import io.github.daniele21.localllm.ui.designsystem.OmbraScaffold
import io.github.daniele21.localllm.ui.designsystem.OmbraSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.OmbraStatusBadge
import io.github.daniele21.localllm.ui.designsystem.OmbraStatusTone
import io.github.daniele21.localllm.ui.designsystem.OmbraTaskProgressStep

@Composable
internal fun OmbraProductApp(viewModel: OmbraProductViewModel, onPickDocument: () -> Unit, onExport: () -> Unit) {
    val workflow by viewModel.workflow.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val definitions by viewModel.definitions.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val harness = ombraHarnessUiStatus(connection.state)

    LaunchedEffect(workflow.stage) {
        if (workflow.stage == OmbraWorkflowStage.REVIEW_READY && review == OmbraReviewUiState.NotReady) {
            viewModel.prepareReview()
        }
    }

    when (workflow.stage) {
        OmbraWorkflowStage.IDLE -> OmbraImportScreen(harness = harness, onPickDocument = onPickDocument)

        OmbraWorkflowStage.EXTRACTING ->
            OmbraAnalysisScreen(
                workflow = workflow,
                harness = harness,
                extractionComplete = false,
                analysisComplete = false,
                onCancel = viewModel::cancel,
            )

        OmbraWorkflowStage.DOCUMENT_SELECTED,
        OmbraWorkflowStage.DEFINITIONS_READY,
        ->
            OmbraDefinitionsScreen(
                task = viewModel.taskSnapshot(),
                state = definitions,
                harness = harness,
                onToggle = { id, selected -> viewModel.toggleDefinition(id, selected) },
                onAddCustom = { draft -> viewModel.addCustomDefinition(draft) },
                onAnalyze = viewModel::startAnalysis,
                onReset = viewModel::reset,
            )

        OmbraWorkflowStage.ANALYZING ->
            OmbraAnalysisScreen(
                workflow = workflow,
                harness = harness,
                extractionComplete = true,
                analysisComplete = false,
                onCancel = viewModel::cancel,
            )

        OmbraWorkflowStage.REVIEW_READY ->
            OmbraReviewScreen(
                state = review,
                harness = harness,
                onPrepareReview = { viewModel.prepareReview() },
                onMove = { delta -> viewModel.moveReview(delta) },
                onToggleReveal = { viewModel.toggleReveal() },
                onDecision = { decision -> viewModel.setReviewDecision(decision) },
                onExport = onExport,
                onReset = { viewModel.reset() },
            )

        OmbraWorkflowStage.EXPORTING ->
            OmbraExportProgressScreen(
                harness = harness,
                onCancel = { viewModel.cancel() },
            )

        OmbraWorkflowStage.EXPORTED ->
            OmbraExportSuccessScreen(
                receipt = workflow.exportReceipt,
                onReset = { viewModel.reset() },
            )

        OmbraWorkflowStage.CANCELLING -> {
            if (workflow.activeOperation?.kind == OmbraOperationKind.EXPORT) {
                OmbraExportProgressScreen(harness = harness, onCancel = {})
            } else {
                OmbraAnalysisScreen(
                    workflow = workflow,
                    harness = harness,
                    extractionComplete = workflow.activeOperation?.kind != OmbraOperationKind.EXTRACTION,
                    analysisComplete = false,
                    onCancel = { false },
                )
            }
        }

        OmbraWorkflowStage.FAILED ->
            OmbraFailureScreen(
                workflow = workflow,
                harness = harness,
                onRetry = viewModel::retry,
                onReturnToReview = viewModel::returnToReview,
                onReset = viewModel::reset,
            )
    }
}

@Composable
private fun OmbraImportScreen(harness: OmbraHarnessUiStatus, onPickDocument: () -> Unit) {
    OmbraScaffold(title = "OMBRA", stepLabel = "Importazione") { innerPadding ->
        OmbraScrollableContent(innerPadding = innerPadding) {
            Text(text = "Proteggi un documento", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Seleziona un PDF. Il documento e i dati rilevati restano su questo dispositivo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OmbraStatusBadge(text = harness.label, tone = harness.tone)
            OmbraDocumentPickerSurface(
                title = "Seleziona un PDF",
                description = "OMBRA analizza il documento localmente.",
                actionLabel = "Importa PDF",
                onClick = onPickDocument,
            )
        }
    }
}

@Composable
private fun OmbraDefinitionsScreen(
    task: OmbraSensitiveTaskSnapshot,
    state: OmbraDefinitionSelectionState,
    harness: OmbraHarnessUiStatus,
    onToggle: (PiiTypeId, Boolean) -> Unit,
    onAddCustom: (PiiDefinitionDraft) -> PiiDefinitionValidation?,
    onAnalyze: () -> Boolean,
    onReset: () -> Boolean,
) {
    var showCustomSheet by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customDefinition by remember { mutableStateOf("") }
    var customExample by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<PiiDefinitionValidation?>(null) }

    OmbraScaffold(
        title = "OMBRA",
        stepLabel = "Dati da proteggere",
        navigationLabel = "Nuovo PDF",
        onNavigationClick = { onReset() },
    ) { innerPadding ->
        OmbraScrollableContent(innerPadding = innerPadding) {
            Text(text = "Scegli cosa rilevare", style = MaterialTheme.typography.headlineLarge)
            task.descriptor?.let { descriptor ->
                Text(
                    text = "${descriptor.displayName} · ${descriptor.pageCount} pagine",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OmbraStatusBadge(text = harness.label, tone = harness.tone)
            state.definitions.forEach { definition ->
                OmbraDefinitionSelectionRow(
                    label = definition.label,
                    definition = definition.definition,
                    selected = definition.id in state.selectedIds,
                    onSelectedChange = { selected -> onToggle(definition.id, selected) },
                )
            }
            OmbraSecondaryButton(
                text = "Aggiungi PII personalizzato",
                onClick = { showCustomSheet = true },
            )
            OmbraPrimaryButton(
                text = "Analizza documento",
                enabled = state.selectedIds.isNotEmpty() && harness.analysisReady,
                onClick = { onAnalyze() },
            )
            if (!harness.analysisReady) {
                Text(
                    text = "L’analisi sarà disponibile quando Harness sarà connesso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showCustomSheet) {
        OmbraDefinitionEditorSheet(
            state =
            OmbraDefinitionEditorState(
                name = customName,
                definition = customDefinition,
                example = customExample,
                nameError = validation?.errorForName(),
                definitionError = validation?.errorForDefinition(),
                exampleError = validation?.errorForExample(),
                canAdd = customName.isNotBlank() && customDefinition.isNotBlank(),
            ),
            title = "Aggiungi PII personalizzato",
            guidance = "Descrivi il tipo di dato da rilevare. L’esempio è facoltativo e rimane in memoria locale.",
            nameLabel = "Nome",
            definitionLabel = "Definizione",
            exampleLabel = "Esempio (facoltativo)",
            addLabel = "Aggiungi",
            cancelLabel = "Annulla",
            onNameChange = { value ->
                customName = value
                validation = null
            },
            onDefinitionChange = { value ->
                customDefinition = value
                validation = null
            },
            onExampleChange = { value ->
                customExample = value
                validation = null
            },
            onAdd = {
                val result =
                    onAddCustom(
                        PiiDefinitionDraft(
                            label = customName,
                            definition = customDefinition,
                            example = customExample.takeIf(String::isNotBlank),
                        ),
                    )
                validation = result
                if (result == null) {
                    customName = ""
                    customDefinition = ""
                    customExample = ""
                    showCustomSheet = false
                }
            },
            onDismiss = {
                validation = null
                showCustomSheet = false
            },
        )
    }
}

@Composable
private fun OmbraAnalysisScreen(
    workflow: OmbraWorkflowState,
    harness: OmbraHarnessUiStatus,
    extractionComplete: Boolean,
    analysisComplete: Boolean,
    onCancel: () -> Boolean,
) {
    OmbraScaffold(title = "OMBRA", stepLabel = "Analisi") { innerPadding ->
        OmbraScrollableContent(innerPadding = innerPadding) {
            Text(text = "Analisi locale in corso", style = MaterialTheme.typography.headlineLarge)
            OmbraStatusBadge(text = harness.label, tone = harness.tone)
            OmbraTaskProgressStep(
                title = "Testo estratto",
                state = if (extractionComplete) OmbraProgressState.COMPLETE else OmbraProgressState.ACTIVE,
                detail = if (extractionComplete) "Documento pronto" else "Lettura del PDF in corso",
            )
            OmbraTaskProgressStep(
                title = "PII in analisi",
                state =
                when {
                    analysisComplete -> OmbraProgressState.COMPLETE
                    extractionComplete && workflow.stage == OmbraWorkflowStage.ANALYZING -> OmbraProgressState.ACTIVE
                    else -> OmbraProgressState.PENDING
                },
            )
            OmbraTaskProgressStep(
                title = "Revisione pronta",
                state = if (analysisComplete) OmbraProgressState.COMPLETE else OmbraProgressState.PENDING,
            )
            if (workflow.stage == OmbraWorkflowStage.CANCELLING) {
                OmbraReviewBanner(
                    title = "Annullamento in corso",
                    detail = "OMBRA sta chiudendo l’operazione locale in modo sicuro.",
                    tone = OmbraStatusTone.REVIEW,
                )
            } else {
                OmbraSecondaryButton(text = "Annulla", onClick = { onCancel() })
            }
        }
    }
}

@Composable
private fun OmbraFailureScreen(
    workflow: OmbraWorkflowState,
    harness: OmbraHarnessUiStatus,
    onRetry: () -> Boolean,
    onReturnToReview: () -> Boolean,
    onReset: () -> Boolean,
) {
    val message = failureMessage(workflow.failureCode)
    OmbraScaffold(title = "OMBRA", stepLabel = "Operazione non completata") { innerPadding ->
        OmbraScrollableContent(innerPadding = innerPadding) {
            Text(
                text = "Non è stato possibile completare l’operazione",
                style = MaterialTheme.typography.headlineLarge,
            )
            OmbraStatusBadge(text = harness.label, tone = harness.tone)
            OmbraReviewBanner(
                title = "Riprova in sicurezza",
                detail = message,
                tone = OmbraStatusTone.ERROR,
            )
            if (workflow.retryTarget == OmbraRetryTarget.EXPORT) {
                OmbraPrimaryButton(text = "Torna alla revisione", onClick = { onReturnToReview() })
            } else {
                OmbraPrimaryButton(text = "Riprova", onClick = { onRetry() })
            }
            OmbraSecondaryButton(text = "Nuovo documento", onClick = { onReset() })
        }
    }
}

@Composable
private fun OmbraScrollableContent(innerPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(LocalOmbraSpacing.current.md),
        verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.md),
    ) {
        content()
    }
}

private fun failureMessage(code: OmbraFailureCode?): String = when (code) {
    OmbraFailureCode.EXTRACTION_FAILED ->
        "Il PDF non può essere letto in modo affidabile. Puoi riprovare o scegliere un nuovo documento."

    OmbraFailureCode.ANALYSIS_FAILED ->
        "L’analisi locale non ha prodotto un risultato valido. Nessun risultato parziale viene usato."

    OmbraFailureCode.EXPORT_FAILED ->
        "L’export non è stato completato. Il file parziale viene rimosso e non viene usato come risultato."

    null -> "L’operazione è stata interrotta senza produrre un risultato utilizzabile."
}
