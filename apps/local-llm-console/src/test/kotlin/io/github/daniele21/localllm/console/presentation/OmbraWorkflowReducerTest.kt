package io.github.daniele21.localllm.console.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraWorkflowReducerTest {
    @Test
    fun lateCallbacksAndDuplicateTerminalsAreIgnoredByOperationId() {
        val first = OmbraWorkflowReducer.reduce(
            OmbraWorkflowState(),
            OmbraWorkflowAction.StartImport(OmbraDocumentSourceRef(1)),
        )
        val firstOperation = requireNotNull(first.state.activeOperation).id

        val reset = OmbraWorkflowReducer.reduce(first.state, OmbraWorkflowAction.ResetRequested)
        val second = OmbraWorkflowReducer.reduce(
            reset.state,
            OmbraWorkflowAction.StartImport(OmbraDocumentSourceRef(2)),
        )
        val secondOperation = requireNotNull(second.state.activeOperation).id
        assertTrue(secondOperation.value > firstOperation.value)

        val late = OmbraWorkflowReducer.reduce(
            second.state,
            OmbraWorkflowAction.ExtractionSucceeded(firstOperation, pageCount = 1, segmentCount = 1),
        )
        assertEquals(second.state, late.state)

        val completed = OmbraWorkflowReducer.reduce(
            second.state,
            OmbraWorkflowAction.ExtractionSucceeded(secondOperation, pageCount = 2, segmentCount = 3),
        )
        assertEquals(OmbraWorkflowStage.DOCUMENT_SELECTED, completed.state.stage)
        assertEquals(2, completed.state.counts.documentPageCount)
        assertEquals(3, completed.state.counts.segmentCount)

        val duplicate = OmbraWorkflowReducer.reduce(
            completed.state,
            OmbraWorkflowAction.ExtractionSucceeded(secondOperation, pageCount = 9, segmentCount = 9),
        )
        assertEquals(completed.state, duplicate.state)
    }

    @Test
    fun cancellationReturnsToSafeStageAndRejectsLateTerminal() {
        val ready = OmbraWorkflowState(
            stage = OmbraWorkflowStage.DEFINITIONS_READY,
            sourceRef = OmbraDocumentSourceRef(7),
            counts = OmbraWorkflowCounts(documentPageCount = 1, segmentCount = 2, activeDefinitionCount = 2),
        )
        val analyzing = OmbraWorkflowReducer.reduce(ready, OmbraWorkflowAction.StartAnalysis)
        val operationId = requireNotNull(analyzing.state.activeOperation).id

        val cancelling = OmbraWorkflowReducer.reduce(analyzing.state, OmbraWorkflowAction.CancelRequested)
        assertEquals(OmbraWorkflowStage.CANCELLING, cancelling.state.stage)
        assertEquals(OmbraWorkflowStage.DEFINITIONS_READY, cancelling.state.cancelReturnStage)
        assertTrue(cancelling.effects.single() is OmbraWorkflowEffect.CancelOperation)

        val cancelled = OmbraWorkflowReducer.reduce(
            cancelling.state,
            OmbraWorkflowAction.CancellationAcknowledged(operationId),
        )
        assertEquals(OmbraWorkflowStage.DEFINITIONS_READY, cancelled.state.stage)
        assertNull(cancelled.state.activeOperation)

        val late = OmbraWorkflowReducer.reduce(
            cancelled.state,
            OmbraWorkflowAction.AnalysisSucceeded(operationId, findingCount = 4, reviewOccurrenceCount = 4),
        )
        assertEquals(cancelled.state, late.state)
    }

    @Test
    fun failureRetryAllocatesANewOperationAndPreservesSafeContext() {
        val ready = OmbraWorkflowState(
            stage = OmbraWorkflowStage.DEFINITIONS_READY,
            sourceRef = OmbraDocumentSourceRef(3),
            counts = OmbraWorkflowCounts(documentPageCount = 1, segmentCount = 1, activeDefinitionCount = 1),
        )
        val analyzing = OmbraWorkflowReducer.reduce(ready, OmbraWorkflowAction.StartAnalysis)
        val failedOperation = requireNotNull(analyzing.state.activeOperation).id
        val failed = OmbraWorkflowReducer.reduce(
            analyzing.state,
            OmbraWorkflowAction.OperationFailed(failedOperation, OmbraFailureCode.ANALYSIS_FAILED),
        )

        assertEquals(OmbraWorkflowStage.FAILED, failed.state.stage)
        assertEquals(OmbraRetryTarget.ANALYSIS, failed.state.retryTarget)

        val retry = OmbraWorkflowReducer.reduce(failed.state, OmbraWorkflowAction.RetryRequested)
        val retryOperation = requireNotNull(retry.state.activeOperation).id
        assertEquals(OmbraWorkflowStage.ANALYZING, retry.state.stage)
        assertTrue(retryOperation.value > failedOperation.value)
        assertTrue(retry.effects.single() is OmbraWorkflowEffect.AnalyzeTask)
    }

    @Test
    fun processRecreationReturnsToImportAndClearsTaskData() {
        val reviewState = OmbraWorkflowState(
            stage = OmbraWorkflowStage.REVIEW_READY,
            sourceRef = OmbraDocumentSourceRef(4),
            exportDestinationRef = OmbraExportDestinationRef(8),
            counts = OmbraWorkflowCounts(
                documentPageCount = 2,
                segmentCount = 4,
                activeDefinitionCount = 3,
                findingCount = 2,
                reviewOccurrenceCount = 2,
            ),
            nextOperationOrdinal = 9,
        )

        val recreated = OmbraWorkflowReducer.reduce(reviewState, OmbraWorkflowAction.ProcessRecreated)

        assertEquals(OmbraWorkflowStage.IDLE, recreated.state.stage)
        assertEquals(9L, recreated.state.nextOperationOrdinal)
        assertEquals(OmbraWorkflowCounts(), recreated.state.counts)
        assertNull(recreated.state.sourceRef)
        assertEquals(
            listOf(
                OmbraWorkflowEffect.ClearSensitiveTask,
                OmbraWorkflowEffect.ReleaseDocumentSources,
            ),
            recreated.effects,
        )
    }
}
