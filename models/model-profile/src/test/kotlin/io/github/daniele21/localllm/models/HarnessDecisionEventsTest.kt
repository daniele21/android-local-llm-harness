package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessDecisionEventsTest {
    @Test
    fun `action required decision must define a recovery action`() {
        val result = runCatching {
            event(
                category = HarnessDecisionCategory.ACTION_REQUIRED,
                action = HarnessDecisionAction.NONE,
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `decision evidence is bounded`() {
        val result = runCatching {
            event(evidence = mapOf("detail" to "x".repeat(257)))
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `decision code must be stable uppercase identifier`() {
        val result = runCatching {
            event(code = "model unavailable")
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `resolution timestamp determines resolved state`() {
        val unresolved = event()
        val resolved = event(resolvedAtEpochMs = 20)

        assertFalse(unresolved.isResolved)
        assertTrue(resolved.isResolved)
    }

    @Test
    fun `preset revision requires preset identity`() {
        val result = runCatching {
            HarnessDecisionContext(presetRevision = 2)
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private fun event(
        category: HarnessDecisionCategory = HarnessDecisionCategory.WARNING,
        action: HarnessDecisionAction = HarnessDecisionAction.REPAIR_PRESET,
        code: String = "PRESET_MODEL_UNAVAILABLE",
        evidence: Map<String, String> = mapOf("source" to "model-resolver"),
        resolvedAtEpochMs: Long? = null,
    ): HarnessDecisionEvent = HarnessDecisionEvent(
        decisionId = HarnessDecisionId("decision-1"),
        category = category,
        code = code,
        title = "Preset unavailable",
        summary = "The selected preset cannot currently resolve its configured model.",
        context = HarnessDecisionContext(
            applicationId = ApplicationId("redactguard"),
            useCaseId = UseCaseId("document-pii-detection"),
            presetId = InferencePresetId("quality"),
            presetRevision = 2,
            bindingRevision = 7,
        ),
        createdAtEpochMs = 10,
        resolvedAtEpochMs = resolvedAtEpochMs,
        dedupeKey = "redactguard|document-pii-detection|quality|PRESET_MODEL_UNAVAILABLE",
        action = action,
        evidence = evidence,
    )
}
