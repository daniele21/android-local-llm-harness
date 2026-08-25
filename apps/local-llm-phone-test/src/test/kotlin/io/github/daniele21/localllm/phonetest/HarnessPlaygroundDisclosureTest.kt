package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessPlaygroundDisclosureTest {
    @Test
    fun `default settings have no validation message`() {
        assertNull(playgroundSettingsValidationMessage(HarnessUiState()))
    }

    @Test
    fun `default sampling has no disabled-control guidance`() {
        assertNull(playgroundSamplingGuidance(HarnessUiState()))
    }

    @Test
    fun `temperature zero explains inactive stochastic controls`() {
        val state = HarnessUiState(playgroundTemperature = "0")

        assertEquals(
            "Temperature 0 disables stochastic sampling; Top-p, Top-k and Min-p are inactive.",
            playgroundSamplingGuidance(state),
        )
    }

    @Test
    fun `invalid temperature exposes the owning validation message without greedy guidance`() {
        val state = HarnessUiState(playgroundTemperature = "3.7")

        assertEquals(
            "Temperature must be between 0.0 and 2.0",
            playgroundSettingsValidationMessage(state),
        )
        assertNull(playgroundSamplingGuidance(state))
    }

    @Test
    fun `invalid context exposes actionable inline validation`() {
        val state = HarnessUiState(playgroundContext = "not-a-number")

        assertEquals(
            "Context must be an integer or blank for Auto",
            playgroundSettingsValidationMessage(state),
        )
    }
}
