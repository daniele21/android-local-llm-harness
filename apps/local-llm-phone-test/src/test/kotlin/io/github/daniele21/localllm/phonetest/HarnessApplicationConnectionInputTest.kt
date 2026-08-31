package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.models.PresetSeedMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessApplicationConnectionInputTest {
    @Test
    fun `signer fingerprint accepts common pasted separators and normalizes case`() {
        val raw = "AA:".repeat(31) + "AA"

        assertEquals("aa".repeat(32), normalizeSignerFingerprint(raw))
    }

    @Test
    fun `signer fingerprint rejects non hex characters`() {
        assertNull(normalizeSignerFingerprint("z".repeat(64)))
    }

    @Test
    fun `generation draft keeps blank values inherited and parses explicit overrides`() {
        val inherited = HarnessPresetGenerationDraft().overridesResult()
        assertTrue(inherited.isSuccess)
        assertNull(inherited.getOrNull())

        val explicit = HarnessPresetGenerationDraft(
            maxOutputTokens = "512",
            temperature = "0.4",
            topP = "0.8",
            thinkingMode = ThinkingMode.ENABLED,
            seedMode = PresetSeedMode.FIXED,
            fixedSeed = "42",
        ).overridesResult().getOrThrow()

        requireNotNull(explicit)
        assertEquals(512, explicit.maxOutputTokens)
        assertEquals(0.4f, explicit.temperature)
        assertEquals(0.8f, explicit.topP)
        assertEquals(ThinkingMode.ENABLED, explicit.thinkingMode)
        assertEquals(PresetSeedMode.FIXED, explicit.seedMode)
        assertEquals(42L, explicit.fixedSeed)
    }

    @Test
    fun `generation draft rejects invalid ranges before save`() {
        assertTrue(HarnessPresetGenerationDraft(temperature = "3").overridesResult().isFailure)
        assertTrue(
            HarnessPresetGenerationDraft(seedMode = PresetSeedMode.FIXED).overridesResult().isFailure,
        )
    }
}
