package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessOverviewEvaluationDiscoveryTest {
    @Test
    fun `ready overview distinguishes prompt check from repeatable evaluation`() {
        val model = ImportedPhoneModel(
            digest = ModelDigest("a".repeat(64)),
            fileName = "qwen.gguf",
            sizeBytes = 1_024L,
            architecture = "qwen35",
            quantization = "Q4_K_M",
        )

        val presentation = harnessOverviewPresentation(
            state = HarnessUiState(importedModel = model),
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            processPss = null,
            thermalStatus = null,
        )

        assertEquals(HarnessOverviewPrimaryAction.RUN_PROMPT, presentation.primaryAction)
        assertTrue(presentation.heroDetail.contains("quick measured check"))
        assertTrue(presentation.heroDetail.contains("Performance"))
        assertTrue(presentation.heroDetail.contains("repeatable evaluation"))
    }
}
