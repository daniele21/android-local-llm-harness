package io.github.daniele21.localllm.console.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OmbraFailurePresentationTest {
    @Test
    fun `extraction failure projects retry without document content`() {
        val presentation =
            OmbraFailureProjector.project(
                failedState(
                    failureCode = OmbraFailureCode.EXTRACTION_FAILED,
                    retryTarget = OmbraRetryTarget.EXTRACTION,
                ),
            )

        assertEquals(
            "Il PDF non può essere letto in modo affidabile. Puoi riprovare o scegliere un nuovo documento.",
            presentation.detail,
        )
        assertEquals(OmbraFailurePrimaryAction.RETRY, presentation.primaryAction)
    }

    @Test
    fun `analysis failure explicitly rejects partial result reuse`() {
        val presentation =
            OmbraFailureProjector.project(
                failedState(
                    failureCode = OmbraFailureCode.ANALYSIS_FAILED,
                    retryTarget = OmbraRetryTarget.ANALYSIS,
                ),
            )

        assertEquals(
            "L’analisi locale non ha prodotto un risultato valido. Nessun risultato parziale viene usato.",
            presentation.detail,
        )
        assertEquals(OmbraFailurePrimaryAction.RETRY, presentation.primaryAction)
    }

    @Test
    fun `export failure returns to review and states partial output cleanup`() {
        val presentation =
            OmbraFailureProjector.project(
                failedState(
                    failureCode = OmbraFailureCode.EXPORT_FAILED,
                    retryTarget = OmbraRetryTarget.EXPORT,
                ),
            )

        assertEquals(
            "L’export non è stato completato. Il file parziale viene rimosso e non viene usato come risultato.",
            presentation.detail,
        )
        assertEquals(OmbraFailurePrimaryAction.RETURN_TO_REVIEW, presentation.primaryAction)
    }

    @Test
    fun `missing typed code stays content-free and follows retry target`() {
        val presentation =
            OmbraFailureProjector.project(
                failedState(
                    failureCode = null,
                    retryTarget = OmbraRetryTarget.ANALYSIS,
                ),
            )

        assertEquals(
            "L’operazione è stata interrotta senza produrre un risultato utilizzabile.",
            presentation.detail,
        )
        assertEquals(OmbraFailurePrimaryAction.RETRY, presentation.primaryAction)
    }

    @Test
    fun `projector rejects non-failed workflow state`() {
        assertThrows(IllegalArgumentException::class.java) {
            OmbraFailureProjector.project(OmbraWorkflowState(stage = OmbraWorkflowStage.IDLE))
        }
    }

    private fun failedState(failureCode: OmbraFailureCode?, retryTarget: OmbraRetryTarget): OmbraWorkflowState = OmbraWorkflowState(
        stage = OmbraWorkflowStage.FAILED,
        retryTarget = retryTarget,
        failureCode = failureCode,
    )
}
