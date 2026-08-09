package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaygroundPresentationTest {
    @Test
    fun everyPhaseHasAnExplicitLabelAndTone() {
        val expectations = mapOf(
            PlaygroundPhase.IDLE to ("Idle" to PlaygroundPresentationTone.NEUTRAL),
            PlaygroundPhase.PREPARING to ("Preparing" to PlaygroundPresentationTone.ACTIVE),
            PlaygroundPhase.QUEUED to ("Queued" to PlaygroundPresentationTone.ACTIVE),
            PlaygroundPhase.GENERATING to ("●  Streaming" to PlaygroundPresentationTone.ACTIVE),
            PlaygroundPhase.COMPLETED to ("Completed" to PlaygroundPresentationTone.SUCCESS),
            PlaygroundPhase.FAILED to ("Failed" to PlaygroundPresentationTone.ERROR),
            PlaygroundPhase.CANCELLED to ("Cancelled" to PlaygroundPresentationTone.WARNING),
        )

        expectations.forEach { (phase, expected) ->
            val presentation = HarnessUiState(
                playground = PlaygroundState(phase = phase),
            ).toPlaygroundPresentation()

            assertEquals(expected.first, presentation.statusLabel)
            assertEquals(expected.second, presentation.statusTone)
        }
    }

    @Test
    fun activePhasesDisableInputsAndUseGeneratingRunLabel() {
        val activePhases = listOf(
            PlaygroundPhase.PREPARING,
            PlaygroundPhase.QUEUED,
            PlaygroundPhase.GENERATING,
        )

        activePhases.forEach { phase ->
            val presentation = HarnessUiState(
                importedModel = testModel(),
                playground = PlaygroundState(
                    phase = phase,
                    cancellationAvailable = true,
                ),
            ).toPlaygroundPresentation()

            assertEquals("Generating…", presentation.runLabel)
            assertFalse(presentation.runEnabled)
            assertFalse(presentation.inputsEnabled)
            assertTrue(presentation.stopVisible)
            assertTrue(presentation.stopEnabled)
        }
    }

    @Test
    fun idlePlaygroundWithoutModelCannotRun() {
        val presentation = HarnessUiState().toPlaygroundPresentation()

        assertEquals("Run locally", presentation.runLabel)
        assertFalse(presentation.runEnabled)
        assertTrue(presentation.inputsEnabled)
        assertFalse(presentation.stopVisible)
        assertFalse(presentation.stopEnabled)
    }

    @Test
    fun idlePlaygroundWithModelCanRun() {
        val presentation = HarnessUiState(
            importedModel = testModel(),
        ).toPlaygroundPresentation()

        assertTrue(presentation.runEnabled)
        assertTrue(presentation.inputsEnabled)
    }

    @Test
    fun controllerBusyDisablesRunAndInputs() {
        val presentation = HarnessUiState(
            importedModel = testModel(),
            controllerBusy = true,
        ).toPlaygroundPresentation()

        assertFalse(presentation.runEnabled)
        assertFalse(presentation.inputsEnabled)
    }

    @Test
    fun completedStateFormatsOutputAndMetrics() {
        val presentation = HarnessUiState(
            importedModel = testModel(),
            playground = PlaygroundState(
                phase = PlaygroundPhase.COMPLETED,
                output = "On-device result",
                detail = "Generation completed",
                metrics = PlaygroundMetrics(
                    queueMs = 5,
                    modelLoadMs = 120,
                    timeToFirstTokenMs = 245,
                    prefillMs = 30,
                    decodeMs = 800,
                    totalMs = 1045,
                    inputTokens = 12,
                    outputTokens = 24,
                    decodeTokensPerSecond = 30.125,
                    modelLoadKind = "COLD",
                ),
            ),
        ).toPlaygroundPresentation()

        assertEquals("On-device result", presentation.responseText)
        assertEquals("Generation completed", presentation.detail)
        assertEquals("245 ms", presentation.ttft)
        assertEquals("1045 ms", presentation.total)
        assertEquals("30.13 tok/s", presentation.decode)
    }

    @Test
    fun failedRuntimeDetailRemainsVisible() {
        val detail = "Model preparation failed: MODEL_LOAD_FAILED: llama.cpp could not load the GGUF model"
        val presentation = HarnessUiState(
            importedModel = testModel(),
            playground = PlaygroundState(
                phase = PlaygroundPhase.FAILED,
                errorCode = "INFERENCE_START_FAILED",
                detail = detail,
            ),
        ).toPlaygroundPresentation()

        assertEquals(detail, presentation.detail)
        assertEquals("Failed", presentation.statusLabel)
        assertEquals(PlaygroundPresentationTone.ERROR, presentation.statusTone)
    }

    @Test
    fun blankOutputAndMissingMetricsUseStableFallbacks() {
        val presentation = HarnessUiState(
            playground = PlaygroundState(
                phase = PlaygroundPhase.FAILED,
                detail = "Inference failed",
            ),
        ).toPlaygroundPresentation()

        assertEquals("Generated output will appear here.", presentation.responseText)
        assertEquals("Unavailable", presentation.ttft)
        assertEquals("Unavailable", presentation.total)
        assertEquals("Unavailable", presentation.decode)
    }

    @Test
    fun cancellationAvailabilityCanExposeStopAfterActivePhase() {
        val presentation = HarnessUiState(
            playground = PlaygroundState(
                phase = PlaygroundPhase.CANCELLED,
                cancellationAvailable = true,
            ),
        ).toPlaygroundPresentation()

        assertTrue(presentation.stopVisible)
        assertTrue(presentation.stopEnabled)
    }

    private fun testModel(): ImportedPhoneModel = ImportedPhoneModel(
        digest = ModelDigest("1".repeat(64)),
        fileName = "test.gguf",
        sizeBytes = 1234,
        architecture = "qwen3",
        quantization = "Q4_K_M",
    )
}
