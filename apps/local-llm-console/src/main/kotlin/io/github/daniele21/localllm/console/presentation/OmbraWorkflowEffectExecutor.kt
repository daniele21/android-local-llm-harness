package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.application.OmbraAnalysisClient
import io.github.daniele21.localllm.console.application.OmbraAnalysisRequest
import io.github.daniele21.localllm.console.application.OmbraDocumentExporter
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractor
import io.github.daniele21.localllm.console.application.OmbraExportRequest
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskStore

internal typealias OmbraOperationGuard = (OmbraOperationId, OmbraOperationKind) -> Boolean

/** Executes reducer effects through replaceable asynchronous application ports. */
internal class OmbraWorkflowEffectExecutor(
    private val extractor: OmbraDocumentExtractor,
    private val analysisClient: OmbraAnalysisClient,
    private val exporter: OmbraDocumentExporter,
    private val taskStore: OmbraSensitiveTaskStore,
) {
    fun execute(effect: OmbraWorkflowEffect, isOperationActive: OmbraOperationGuard, emit: (OmbraWorkflowAction) -> Unit) {
        when (effect) {
            is OmbraWorkflowEffect.ExtractDocument -> executeExtraction(effect, isOperationActive, emit)
            is OmbraWorkflowEffect.AnalyzeTask -> executeAnalysis(effect, isOperationActive, emit)
            is OmbraWorkflowEffect.ExportTask -> executeExport(effect, isOperationActive, emit)
            is OmbraWorkflowEffect.CancelOperation -> executeCancellation(effect, emit)
            OmbraWorkflowEffect.ClearSensitiveTask -> taskStore.clear()
        }
    }

    private fun executeExtraction(
        effect: OmbraWorkflowEffect.ExtractDocument,
        isOperationActive: OmbraOperationGuard,
        emit: (OmbraWorkflowAction) -> Unit,
    ) {
        extractor.extract(effect.operationId, effect.sourceRef) { result ->
            if (!isOperationActive(effect.operationId, OmbraOperationKind.EXTRACTION)) return@extract
            result.fold(
                onSuccess = { document ->
                    if (runCatching { taskStore.replaceDocument(document) }.isSuccess) {
                        emit(
                            OmbraWorkflowAction.ExtractionSucceeded(
                                operationId = effect.operationId,
                                pageCount = document.descriptor.pageCount,
                                segmentCount = document.segments.size,
                            ),
                        )
                    } else {
                        emit(OmbraWorkflowAction.OperationFailed(effect.operationId, OmbraFailureCode.EXTRACTION_FAILED))
                    }
                },
                onFailure = {
                    emit(OmbraWorkflowAction.OperationFailed(effect.operationId, OmbraFailureCode.EXTRACTION_FAILED))
                },
            )
        }
    }

    private fun executeAnalysis(
        effect: OmbraWorkflowEffect.AnalyzeTask,
        isOperationActive: OmbraOperationGuard,
        emit: (OmbraWorkflowAction) -> Unit,
    ) {
        val snapshot = taskStore.snapshot()
        val request = runCatching { OmbraAnalysisRequest(snapshot.segments, snapshot.definitions) }.getOrNull()
        if (request == null) {
            emit(OmbraWorkflowAction.OperationFailed(effect.operationId, OmbraFailureCode.ANALYSIS_FAILED))
            return
        }
        analysisClient.analyze(effect.operationId, request) { result ->
            if (!isOperationActive(effect.operationId, OmbraOperationKind.ANALYSIS)) return@analyze
            result.fold(
                onSuccess = { findings ->
                    if (runCatching { taskStore.replaceFindings(findings) }.isSuccess) {
                        emit(
                            OmbraWorkflowAction.AnalysisSucceeded(
                                operationId = effect.operationId,
                                findingCount = findings.size,
                                reviewOccurrenceCount = taskStore.snapshot().reviewOccurrences.size,
                            ),
                        )
                    } else {
                        emit(OmbraWorkflowAction.OperationFailed(effect.operationId, OmbraFailureCode.ANALYSIS_FAILED))
                    }
                },
                onFailure = {
                    emit(OmbraWorkflowAction.OperationFailed(effect.operationId, OmbraFailureCode.ANALYSIS_FAILED))
                },
            )
        }
    }

    private fun executeExport(
        effect: OmbraWorkflowEffect.ExportTask,
        isOperationActive: OmbraOperationGuard,
        emit: (OmbraWorkflowAction) -> Unit,
    ) {
        val snapshot = taskStore.snapshot()
        val descriptor = snapshot.descriptor
        if (descriptor == null || snapshot.segments.isEmpty()) {
            emit(OmbraWorkflowAction.OperationFailed(effect.operationId, OmbraFailureCode.EXPORT_FAILED))
            return
        }
        val request = OmbraExportRequest(descriptor, snapshot.segments, snapshot.reviewOccurrences)
        exporter.export(effect.operationId, effect.destinationRef, request) { result ->
            if (!isOperationActive(effect.operationId, OmbraOperationKind.EXPORT)) return@export
            result.fold(
                onSuccess = { receipt -> emit(OmbraWorkflowAction.ExportSucceeded(effect.operationId, receipt)) },
                onFailure = {
                    emit(OmbraWorkflowAction.OperationFailed(effect.operationId, OmbraFailureCode.EXPORT_FAILED))
                },
            )
        }
    }

    private fun executeCancellation(effect: OmbraWorkflowEffect.CancelOperation, emit: (OmbraWorkflowAction) -> Unit) {
        val onCancelled = { emit(OmbraWorkflowAction.CancellationAcknowledged(effect.operationId)) }
        when (effect.operationKind) {
            OmbraOperationKind.EXTRACTION -> extractor.cancel(effect.operationId, onCancelled)
            OmbraOperationKind.ANALYSIS -> analysisClient.cancel(effect.operationId, onCancelled)
            OmbraOperationKind.EXPORT -> exporter.cancel(effect.operationId, onCancelled)
        }
    }
}
